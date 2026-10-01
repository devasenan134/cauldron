from collections.abc import Callable

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, SQLModel, and_, col, or_, select

from ..db import get_session
from ..deps import current_user
from ..models import Food, Ingredient, Recipe, RecipeBase, Step, User
from ..nutrition import macros, source_of
from ..prep import Kitchen, nutrition_for
from ..recipe_edit import RecipeIn, relink_recipe, save_recipe
from ..users import sees_library

router = APIRouter()

# Recipes from this source form a library only the guest list sees (CAULDRON_LIBRARY): it's someone
# else's work.
SHARED_SOURCE = "cookwell"
# The starter recipes (app/starter/recipes.json), written for Cauldron: everyone sees them.
STARTER_SOURCE = "starter"
# Library recipes are read-only: nobody rewrites or deletes them in the app (the owner can still fix
# ingredient weights and foods). They aren't anyone's "mine" either.
LIBRARY_SOURCES = (SHARED_SOURCE, STARTER_SOURCE)


class RecipeSummary(SQLModel):
    id: int
    title: str
    image_url: str | None
    source: str
    cuisine: str | None
    category: str | None
    total_minutes: int | None
    servings: float | None
    tags: list[str]
    kcal_per_serving: float | None
    is_prep: bool = False


class IngredientOut(SQLModel):
    id: int
    position: int
    group: str | None
    name: str
    note: str
    label: str
    grams: float | None
    grams_source: str | None
    aisle: str | None
    food_id: int | None
    food_name: str | None
    food_edited: bool = False  # your own version of the food
    prep_id: int | None = None  # made from this prepped-ingredient recipe
    prep_title: str | None = None
    nutrition: dict[str, float] | None


class Nutrition(SQLModel):
    total: dict[str, float]
    per_serving: dict[str, float] | None
    left_out: list[str]  # have a food or weight but not both, so not counted
    estimated: list[str]  # counted using a typical amount ("a drizzle")
    grams: float = 0  # weight of what's counted
    yield_grams: float | None = None  # a prep: what it weighs when done
    per_100g: dict[str, float] | None = None  # a prep: per 100 g, for the recipes that use it


class RecipeDetail(RecipeBase):
    can_edit: bool
    favorite: bool = False
    folder_ids: list[int] = []
    parent_id: int | None = None
    parent_title: str | None = None
    variations: list[dict] = []  # your versions of this recipe: [{"id", "title"}]
    used_in: list[dict] = []  # a prep: your recipes that use it, [{"id", "title"}]
    id: int
    ingredients: list[IngredientOut]
    steps: list[Step]
    nutrition: Nutrition


class IngredientPatch(SQLModel):
    grams: float | None = None
    food_id: int | None = None
    prep_id: int | None = None  # use a prepped-ingredient recipe (clears the food)


class PrepPatch(SQLModel):
    is_prep: bool | None = None
    yield_grams: float | None = None


def shared_sources(user: User) -> list[str]:
    """The libraries this user sees: the starter recipes, and Cook Well for the guest list."""
    return [STARTER_SOURCE, SHARED_SOURCE] if sees_library(user) else [STARTER_SOURCE]


def visible(user: User):
    """Your own recipes plus the libraries you may see (not the ones the owner hid)."""
    return and_(Recipe.hidden == False, or_(Recipe.owner_id == user.id, col(Recipe.source).in_(shared_sources(user))))  # noqa: E712


def not_library():
    return col(Recipe.source).not_in(LIBRARY_SOURCES)


def owned_recipe(session: Session, recipe_id: int, user: User, edit: bool = False) -> Recipe:
    """A recipe the user may see (or, with edit=True, change); 404 otherwise."""
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or not can_see(recipe, user):
        raise HTTPException(404, "recipe not found")
    if edit and not can_edit(recipe, user):
        raise HTTPException(403, "only the owner can edit this recipe")
    return recipe


def can_see(recipe: Recipe, user: User) -> bool:
    if recipe.hidden:  # a hidden library recipe: only the owner, to edit it or bring it back
        return recipe.owner_id == user.id
    return recipe.owner_id == user.id or recipe.source in shared_sources(user)


def can_edit(recipe: Recipe, user: User) -> bool:
    return recipe.owner_id == user.id


def recipe_detail(session: Session, recipe: Recipe, user: User) -> RecipeDetail:
    kitchen = Kitchen(session, user, [recipe])
    ingredients = kitchen.ings.get(recipe.id, [])
    steps = session.exec(select(Step).where(Step.recipe_id == recipe.id).order_by(Step.position)).all()
    foods = kitchen.foods
    preps = {i.prep_id: kitchen.per100(i.prep_id) for i in ingredients if i.prep_id}
    out = [IngredientOut(**i.model_dump(), food_name=foods[i.food_id].name if i.food_id in foods else None,
                         food_edited=i.food_id in foods and foods[i.food_id].base_id is not None,
                         prep_title=kitchen.recipes[i.prep_id].title if i.prep_id in kitchen.recipes else None,
                         nutrition=macros(i.grams, source_of(i, foods, preps))) for i in ingredients]
    from ..models import Favorite, Folder, FolderRecipe
    parent = session.get(Recipe, recipe.parent_id) if recipe.parent_id else None
    extra = dict(
        favorite=session.exec(select(Favorite).where(Favorite.owner_id == user.id, Favorite.recipe_id == recipe.id)).first() is not None,
        folder_ids=list(session.exec(select(FolderRecipe.folder_id).join(Folder, Folder.id == FolderRecipe.folder_id)
                                     .where(Folder.owner_id == user.id, FolderRecipe.recipe_id == recipe.id))),
        parent_title=parent.title if parent and can_see(parent, user) else None,
        variations=[{"id": v.id, "title": v.title} for v in session.exec(
            select(Recipe).where(Recipe.parent_id == recipe.id, Recipe.owner_id == user.id))],
        used_in=[{"id": r.id, "title": r.title} for r in session.exec(
            select(Recipe).where(visible(user), col(Recipe.id).in_(select(Ingredient.recipe_id).where(Ingredient.prep_id == recipe.id)))
            .order_by(Recipe.title))] if recipe.is_prep else [],
    )
    return RecipeDetail(**recipe.model_dump(), **extra, can_edit=can_edit(recipe, user), ingredients=out, steps=steps,
                        nutrition=Nutrition(**kitchen.nutrition(recipe.id)))


# Cook Well's tags come in families. How a family combines when you pick more than one of its tags:
# "one" (pick one: the tags contradict each other), "any" (OR: chicken or beef) or "all" (AND: gluten
# free and dairy free). Families always combine with AND. Time isn't a family: it comes from the
# recipe's minutes (see TIME_RANGES), so the filters have one idea of time, not two that can disagree.
TAG_GROUPS = {
    "Difficulty": ["Easy", "Level Up"],
    "Mood": ["Feel Good", "Bad Day", "Happy", "Lazy", "Curious", "Guilty", "Party", "Impress", "Down",
             "Energized", "Chill", "Date Night"],
    "Protein": ["Chicken", "Beef", "Pork", "Eggs", "Seafood", "Vegetarian"],
    "Method": ["Stir Fry", "Bake", "Braise", "Sear", "Crispy", "Grill", "Deep Fry", "Framework"],
    "Diet": ["High Protein", "Gluten Free", "Low Fat", "Low Carb", "Dairy Free"],
}
GROUP_MODE = {"Difficulty": "one", "Diet": "all"}  # the rest: "any"
MORE = "More"  # tags in no family (used by 3+ recipes), offered together as "any"
GROUP_OF = {t.lower(): g for g, tags in TAG_GROUPS.items() for t in tags}
# Cook Well's time tags, as minutes: what a recipe without minutes is taken to need. As filters they're
# folded into "Ready in" (time=…); an old link that still sends one gets the same range.
TIME_TAGS = {"quick": (None, 30), "under 1 hour": (31, 60), "i got time": (61, None)}

# The presets the apps offer, by key: "Ready in" (minutes, both ends included) and calories per serving
# (whole kcal as shown on the card, min included, max not, so 400 kcal is "400–700", never also "Under 400").
TIME_RANGES = {"15": (None, 15), "30": (None, 30), "45": (None, 45), "60": (None, 60), "long": (61, None)}
KCAL_RANGES = {"light": (None, 400), "medium": (400, 700), "hearty": (700, None)}

SORTS = {"title", "quickest", "lowest_kcal", "highest_protein", "newest"}


def minutes_of(r: Recipe) -> int | None:
    """How long a recipe takes: its minutes, or failing that what its time tag says (the top of the range)."""
    if r.total_minutes:
        return r.total_minutes
    for t in r.tags or []:
        if (span := TIME_TAGS.get(t.lower())) is not None:
            return span[1] or span[0]
    return None


def in_range(x: float | None, lo: float | None, hi: float | None, hi_open: bool = False) -> bool:
    if x is None:
        return False
    return (lo is None or x >= lo) and (hi is None or (x < hi if hi_open else x <= hi))


class Filter:
    """One filter, as both endpoints read it. Each part is a test on a recipe, kept apart by name so the
    facets can leave one part out ("how many would I see if I picked that instead?")."""

    def __init__(self, cuisine: list[str], category: list[str], tag: list[str], time: str | None,
                 max_minutes: int | None, min_kcal: float | None, max_kcal: float | None, kcal: str | None,
                 mine: bool, prep: bool | None):
        self.cuisines = {c for c in cuisine if c}
        self.categories = {c for c in category if c}
        self.mine, self.prep = mine, prep
        # Tags by family; the time tags become a range of minutes.
        self.tags: dict[str, set[str]] = {}
        minutes: tuple[int | None, int | None] | None = None
        for t in (t.strip() for t in tag):
            if not t:
                continue
            if t.lower() in TIME_TAGS:
                minutes = TIME_TAGS[t.lower()]
            else:
                self.tags.setdefault(GROUP_OF.get(t.lower(), MORE), set()).add(t.lower())
        if time:
            if time not in TIME_RANGES:
                raise HTTPException(422, f"time must be one of {', '.join(TIME_RANGES)}")
            minutes = TIME_RANGES[time]
        elif max_minutes:
            minutes = (None, max_minutes)
        self.minutes = minutes
        if kcal:
            if kcal not in KCAL_RANGES:
                raise HTTPException(422, f"kcal must be one of {', '.join(KCAL_RANGES)}")
            min_kcal, max_kcal = KCAL_RANGES[kcal]
        if min_kcal is not None and max_kcal is not None and min_kcal >= max_kcal:
            raise HTTPException(422, "min_kcal must be less than max_kcal")
        self.kcal = (min_kcal, max_kcal) if min_kcal is not None or max_kcal is not None else None

    def tests(self, user: User, kcal_of) -> dict[str, Callable[[Recipe], bool]]:
        t: dict[str, Callable[[Recipe], bool]] = {}
        if self.mine:
            t["mine"] = lambda r: r.owner_id == user.id and r.source not in LIBRARY_SOURCES
        if self.prep is not None:
            t["prep"] = lambda r: bool(r.is_prep) == self.prep
        if self.cuisines:
            t["cuisine"] = lambda r: r.cuisine in self.cuisines
        if self.categories:
            t["category"] = lambda r: r.category in self.categories
        if self.minutes:
            t["time"] = lambda r, lo=self.minutes[0], hi=self.minutes[1]: in_range(minutes_of(r), lo, hi)
        if self.kcal:
            t["kcal"] = lambda r, lo=self.kcal[0], hi=self.kcal[1]: in_range(
                None if (k := kcal_of(r)) is None else round(k), lo, hi, hi_open=True)
        for g, want in self.tags.items():
            if GROUP_MODE.get(g) == "all":
                t["tag:" + g] = lambda r, want=want: want <= {x.lower() for x in r.tags or []}
            else:
                t["tag:" + g] = lambda r, want=want: bool(want & {x.lower() for x in r.tags or []})
        return t


def filter_params(
    cuisine: list[str] = Query(default=[]),
    category: list[str] = Query(default=[]),
    tag: list[str] = Query(default=[]),
    time: str | None = None,  # a key of TIME_RANGES
    max_minutes: int | None = None,  # older apps: "Ready in" as minutes
    kcal: str | None = None,  # a key of KCAL_RANGES
    min_kcal: float | None = None,  # older apps: calories as numbers (min included, max not)
    max_kcal: float | None = None,
    mine: bool = False,
    prep: bool | None = None,  # true: only prepped ingredients; false: leave them out
) -> Filter:
    return Filter(cuisine, category, tag, time, max_minutes, min_kcal, max_kcal, kcal, mine, prep)


def searched(session: Session, user: User, q: str | None) -> list[Recipe]:
    """The recipes you can see, narrowed by the search box (title or an ingredient)."""
    stmt = select(Recipe).where(visible(user))
    if q and (q := q.strip()):
        like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        in_ingredients = select(Ingredient.recipe_id).where(col(Ingredient.name).ilike(like, escape="\\"))
        stmt = stmt.where(or_(col(Recipe.title).ilike(like, escape="\\"), col(Recipe.id).in_(in_ingredients)))
    return list(session.exec(stmt))


def title_key(r: Recipe):
    return (r.title.casefold(), r.id)


@router.get("/recipes", response_model=list[RecipeSummary])
def list_recipes(
    q: str | None = None,
    sort: str = "title",
    f: Filter = Depends(filter_params),
    session: Session = Depends(get_session), user: User = Depends(current_user),
):
    recipes = searched(session, user, q)
    nutrition = nutrition_for(session, recipes, user)

    def per(r: Recipe, key: str) -> float | None:
        return (nutrition[r.id]["per_serving"] or {}).get(key)

    tests = f.tests(user, lambda r: per(r, "kcal")).values()
    recipes = sorted((r for r in recipes if all(t(r) for t in tests)), key=title_key)
    # Recipes without the number sorted by always go last; ties keep A–Z.
    if sort == "quickest":
        recipes.sort(key=lambda r: (minutes_of(r) is None, minutes_of(r) or 0))
    elif sort == "lowest_kcal":
        recipes.sort(key=lambda r: (per(r, "kcal") is None, per(r, "kcal") or 0))
    elif sort == "highest_protein":
        recipes.sort(key=lambda r: (per(r, "protein") is None, -(per(r, "protein") or 0)))
    elif sort == "newest":
        recipes.sort(key=lambda r: (r.created_at, r.id), reverse=True)
    return [RecipeSummary(**r.model_dump(), kcal_per_serving=per(r, "kcal")) for r in recipes]


@router.get("/recipes/facets")
def recipe_facets(
    q: str | None = None,
    f: Filter = Depends(filter_params),
    session: Session = Depends(get_session), user: User = Depends(current_user),
):
    """What the filters offer: cuisines, meals, and tags in their families (only ones in use), and for the
    filter given (the same parameters as GET /recipes), how many recipes each option would show:
    for an option in a pick-one or OR family, if you picked it (the family's other picks left out);
    in an AND family, if you added it. `total` is how many the filter shows now."""
    every = list(session.exec(select(Recipe).where(visible(user))))
    used: dict[str, int] = {}
    for r in every:
        for t in r.tags or []:
            used[t] = used.get(t, 0) + 1
    groups = [{"name": g, "mode": GROUP_MODE.get(g, "any"), "tags": [t for t in tags if t in used]} for g, tags in TAG_GROUPS.items()]
    known = {t.lower() for tags in TAG_GROUPS.values() for t in tags} | set(TIME_TAGS)
    other = sorted((t for t in used if t.lower() not in known and used[t] >= 3), key=lambda t: (-used[t], t))
    if other:
        groups.append({"name": MORE, "mode": "any", "tags": other})

    recipes = searched(session, user, q)
    nutrition = nutrition_for(session, recipes, user)

    def kcal_of(r: Recipe) -> float | None:
        return (nutrition[r.id]["per_serving"] or {}).get("kcal")

    tests = f.tests(user, kcal_of)

    def passing(*leave_out: str) -> list[Recipe]:
        keep = [t for k, t in tests.items() if k not in leave_out]
        return [r for r in recipes if all(t(r) for t in keep)]

    def tally(rs: list[Recipe], value) -> dict[str, int]:
        out: dict[str, int] = {}
        for r in rs:
            for v in value(r):
                if v is not None:
                    out[v] = out.get(v, 0) + 1
        return out

    tag_counts: dict[str, int] = {}
    for g in groups:
        rs = passing() if g["mode"] == "all" else passing("tag:" + g["name"])
        names = {t.lower(): t for t in g["tags"]}
        for t, n in tally(rs, lambda r: {x.lower() for x in r.tags or []}).items():
            if t in names:
                tag_counts[names[t]] = n
    rs = passing("time")
    times = [minutes_of(r) for r in rs]
    rs = passing("kcal")
    kcals = [None if (k := kcal_of(r)) is None else round(k) for r in rs]
    owner = Filter([], [], [], None, None, None, None, None, True, None).tests(user, kcal_of)["mine"]
    counts = {
        "cuisine": tally(passing("cuisine"), lambda r: [r.cuisine]),
        "category": tally(passing("category"), lambda r: [r.category]),
        "tag": tag_counts,
        "time": {k: sum(in_range(m, lo, hi) for m in times) for k, (lo, hi) in TIME_RANGES.items()},
        "kcal": {k: sum(in_range(x, lo, hi, hi_open=True) for x in kcals) for k, (lo, hi) in KCAL_RANGES.items()},
        "mine": sum(owner(r) for r in passing("mine")),
        "prep": sum(bool(r.is_prep) for r in passing("prep")),
    }
    return {"cuisines": sorted({r.cuisine for r in every if r.cuisine}),
            "categories": sorted({r.category for r in every if r.category}),
            "tag_groups": [g for g in groups if g["tags"]], "total": len(passing()), "counts": counts}


@router.get("/recipes/{recipe_id}", response_model=RecipeDetail)
def get_recipe(recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    return recipe_detail(session, owned_recipe(session, recipe_id, user), user)


@router.patch("/ingredients/{ingredient_id}", response_model=RecipeDetail)
def patch_ingredient(ingredient_id: int, patch: IngredientPatch,
                     session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Set an ingredient's weight, food or prep by hand. Returns the updated recipe."""
    ing = session.get(Ingredient, ingredient_id)
    if ing is None:
        raise HTTPException(404, "ingredient not found")
    recipe = owned_recipe(session, ing.recipe_id, user, edit=True)
    fields = patch.model_dump(exclude_unset=True)
    # A new food or prep can mean a new weight ("2 cloves" of a different food), unless you set one.
    relink = ("food_id" in fields or "prep_id" in fields) and "grams" not in fields
    if "food_id" in fields:
        if fields["food_id"] is not None:
            food = session.get(Food, fields["food_id"])
            if food is None or food.owner_id not in (None, user.id):
                raise HTTPException(400, "unknown food")
            ing.food_id = food.base_id or food.id  # your version applies through the shared food
            ing.prep_id = None
        else:
            ing.food_id = None
    if "prep_id" in fields:
        if fields["prep_id"] is not None:
            prep = owned_recipe(session, fields["prep_id"], user)
            if not prep.is_prep or prep.id == recipe.id:
                raise HTTPException(400, "that recipe isn't a prepped ingredient")
            ing.prep_id, ing.food_id = prep.id, None
        elif ing.prep_id is not None:
            ing.prep_id = None
            if "food_id" not in fields:  # back to a plain ingredient: find it a food
                from ..foodlink import food_index
                from ..recipe_edit import guess_food
                food = guess_food(session, ing.name, food_index(session), owner_id=user.id)
                ing.food_id = food.id if food else None
    if "grams" in fields:
        ing.grams = fields["grams"]
        ing.grams_source = "manual" if fields["grams"] is not None else None
    session.add(ing)
    if relink:
        relink_recipe(session, recipe, {ing.id})
    session.commit()
    return recipe_detail(session, recipe, user)


@router.patch("/recipes/{recipe_id}/prep", response_model=RecipeDetail)
def set_prep(recipe_id: int, patch: PrepPatch, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Mark a recipe as a prepped ingredient (or not), and set what it weighs when done."""
    recipe = owned_recipe(session, recipe_id, user, edit=True)
    fields = patch.model_dump(exclude_unset=True)
    if fields.get("is_prep") is not None:
        recipe.is_prep = fields["is_prep"]
    if "yield_grams" in fields:
        recipe.yield_grams = fields["yield_grams"] if fields["yield_grams"] and fields["yield_grams"] > 0 else None
    session.add(recipe)
    session.commit()
    return recipe_detail(session, recipe, user)


@router.post("/recipes", response_model=RecipeDetail)
def create_recipe(body: RecipeIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """A recipe of your own (only you see it)."""
    if not body.title.strip():
        raise HTTPException(400, "a recipe needs a title")
    recipe = save_recipe(session, Recipe(owner_id=user.id, source="manual", title=body.title, slug=""), body)
    return recipe_detail(session, recipe, user)


@router.put("/recipes/{recipe_id}", response_model=RecipeDetail)
def update_recipe(recipe_id: int, body: RecipeIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Rewrite your recipe. The libraries are the owner's, so only the owner rewrites those."""
    recipe = owned_recipe(session, recipe_id, user, edit=True)
    if not body.title.strip():
        raise HTTPException(400, "a recipe needs a title")
    slug = recipe.slug
    if recipe.source == STARTER_SOURCE:
        recipe.edited = True  # so rewriting the starter recipes from recipes.json keeps this version
    recipe = save_recipe(session, recipe, body)
    if recipe.source in LIBRARY_SOURCES and recipe.slug != slug:
        recipe.slug = slug  # a library recipe keeps its slug: seeding finds it by that
        session.add(recipe)
        session.commit()
    return recipe_detail(session, recipe, user)


@router.delete("/recipes/{recipe_id}")
def delete_recipe(recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    recipe = owned_recipe(session, recipe_id, user, edit=True)
    if recipe.source in LIBRARY_SOURCES:
        raise HTTPException(403, "library recipes can't be deleted; hide them in Settings → Recipe libraries instead")
    session.delete(recipe)
    session.commit()
    return {"ok": True}

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, SQLModel, col, or_, select

from ..db import get_session
from ..deps import current_user
from ..models import Food, Ingredient, Recipe, RecipeBase, Step, User
from ..nutrition import macros, source_of
from ..prep import Kitchen, nutrition_for
from ..recipe_edit import RecipeIn, relink_recipe, save_recipe
from ..users import sees_library

router = APIRouter()

# Recipes from this source form a library every signed-in user can see.
SHARED_SOURCE = "cookwell"


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


def visible(user: User):
    """Your own recipes plus the shared Cook Well library (for those who may see it)."""
    return or_(Recipe.owner_id == user.id, Recipe.source == SHARED_SOURCE) if sees_library(user) else Recipe.owner_id == user.id


def owned_recipe(session: Session, recipe_id: int, user: User, edit: bool = False) -> Recipe:
    """A recipe the user may see (or, with edit=True, change); 404 otherwise."""
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or not can_see(recipe, user):
        raise HTTPException(404, "recipe not found")
    if edit and not can_edit(recipe, user):
        raise HTTPException(403, "only the owner can edit this recipe")
    return recipe


def can_see(recipe: Recipe, user: User) -> bool:
    return recipe.owner_id == user.id or (recipe.source == SHARED_SOURCE and sees_library(user))


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


# Cook Well's tags come in families; filters are OR within a family and AND across them.
TAG_GROUPS = {
    "Difficulty": ["Easy", "Level Up"],
    "Time": ["Quick", "Under 1 Hour", "I Got Time"],
    "Mood": ["Feel Good", "Bad Day", "Happy", "Lazy", "Curious", "Guilty", "Party", "Impress", "Down",
             "Energized", "Chill", "Date Night"],
    "Protein": ["Chicken", "Beef", "Pork", "Eggs", "Seafood", "Vegetarian"],
    "Method": ["Stir Fry", "Bake", "Braise", "Sear", "Crispy", "Grill", "Deep Fry", "Framework"],
    "Diet": ["High Protein", "Gluten Free", "Low Fat", "Low Carb", "Dairy Free"],
}
GROUP_OF = {t.lower(): g for g, tags in TAG_GROUPS.items() for t in tags}

SORTS = {"title", "quickest", "lowest_kcal", "highest_protein", "newest"}


@router.get("/recipes", response_model=list[RecipeSummary])
def list_recipes(
    q: str | None = None,
    cuisine: list[str] = Query(default=[]),
    category: list[str] = Query(default=[]),
    tag: list[str] = Query(default=[]),
    max_minutes: int | None = None,
    min_kcal: float | None = None,
    max_kcal: float | None = None,
    mine: bool = False,
    prep: bool | None = None,  # true: only prepped ingredients; false: leave them out
    sort: str = "title",
    session: Session = Depends(get_session), user: User = Depends(current_user),
):
    stmt = select(Recipe).where(Recipe.owner_id == user.id, Recipe.source != SHARED_SOURCE) if mine else select(Recipe).where(visible(user))
    if q:
        like = f"%{q}%"
        in_ingredients = select(Ingredient.recipe_id).where(col(Ingredient.name).ilike(like))
        stmt = stmt.where(or_(col(Recipe.title).ilike(like), col(Recipe.id).in_(in_ingredients)))
    if cuisine := [c for c in cuisine if c]:
        stmt = stmt.where(col(Recipe.cuisine).in_(cuisine))
    if category := [c for c in category if c]:
        stmt = stmt.where(col(Recipe.category).in_(category))
    if prep is not None:
        stmt = stmt.where(Recipe.is_prep == prep)
    if max_minutes:
        stmt = stmt.where(col(Recipe.total_minutes).is_not(None), Recipe.total_minutes <= max_minutes)
    recipes = list(session.exec(stmt.order_by(Recipe.title)))
    if tag := [t for t in tag if t]:
        # OR within a tag family, AND across families.
        groups: dict[str, set[str]] = {}
        for t in tag:
            groups.setdefault(GROUP_OF.get(t.lower(), t.lower()), set()).add(t.lower())
        recipes = [r for r in recipes if all({x.lower() for x in r.tags} & want for want in groups.values())]
    nutrition = nutrition_for(session, recipes, user)

    def per(r: Recipe, key: str) -> float | None:
        return (nutrition[r.id]["per_serving"] or {}).get(key)

    if min_kcal is not None or max_kcal is not None:
        recipes = [r for r in recipes if (k := per(r, "kcal")) is not None
                   and (min_kcal is None or k >= min_kcal) and (max_kcal is None or k <= max_kcal)]
    if sort == "quickest":
        recipes.sort(key=lambda r: (r.total_minutes is None, r.total_minutes or 0))
    elif sort == "lowest_kcal":
        recipes.sort(key=lambda r: (per(r, "kcal") is None, per(r, "kcal") or 0))
    elif sort == "highest_protein":
        recipes.sort(key=lambda r: -(per(r, "protein") or 0))
    elif sort == "newest":
        recipes.sort(key=lambda r: r.created_at, reverse=True)
    return [RecipeSummary(**r.model_dump(), kcal_per_serving=per(r, "kcal")) for r in recipes]


@router.get("/recipes/facets")
def recipe_facets(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """What the filters offer: cuisines, meals, and tags in their families (only ones in use)."""
    def distinct(column):
        return sorted(v for v in session.exec(select(column).where(visible(user)).distinct()) if v)
    used: dict[str, int] = {}
    for tags in session.exec(select(Recipe.tags).where(visible(user))):
        for t in tags or []:
            used[t] = used.get(t, 0) + 1
    groups = [{"name": g, "tags": [t for t in tags if t in used]} for g, tags in TAG_GROUPS.items()]
    known = {t for tags in TAG_GROUPS.values() for t in tags}
    other = sorted((t for t in used if t not in known and used[t] >= 3), key=lambda t: -used[t])
    if other:
        groups.append({"name": "More", "tags": other})
    return {"cuisines": distinct(Recipe.cuisine), "categories": distinct(Recipe.category),
            "tag_groups": [g for g in groups if g["tags"]]}


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
    recipe = owned_recipe(session, recipe_id, user, edit=True)
    if recipe.source == SHARED_SOURCE:
        raise HTTPException(403, "library recipes can't be rewritten; fix ingredient weights and foods instead")
    if not body.title.strip():
        raise HTTPException(400, "a recipe needs a title")
    return recipe_detail(session, save_recipe(session, recipe, body), user)


@router.delete("/recipes/{recipe_id}")
def delete_recipe(recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    recipe = owned_recipe(session, recipe_id, user, edit=True)
    if recipe.source == SHARED_SOURCE:
        raise HTTPException(403, "library recipes can't be deleted")
    session.delete(recipe)
    session.commit()
    return {"ok": True}

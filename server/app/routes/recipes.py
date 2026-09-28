from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, SQLModel, col, or_, select

from ..db import get_session
from ..deps import current_user
from ..models import Food, Ingredient, Recipe, RecipeBase, Step, User
from ..nutrition import macros, recipe_nutrition
from ..recipe_edit import RecipeIn, save_recipe

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
    nutrition: dict[str, float] | None


class Nutrition(SQLModel):
    total: dict[str, float]
    per_serving: dict[str, float] | None
    left_out: list[str]  # have a food or weight but not both, so not counted
    estimated: list[str]  # counted using a typical amount ("a drizzle")


class RecipeDetail(RecipeBase):
    can_edit: bool
    favorite: bool = False
    folder_ids: list[int] = []
    parent_id: int | None = None
    parent_title: str | None = None
    variations: list[dict] = []  # your versions of this recipe: [{"id", "title"}]
    id: int
    ingredients: list[IngredientOut]
    steps: list[Step]
    nutrition: Nutrition


class IngredientPatch(SQLModel):
    grams: float | None = None
    food_id: int | None = None


class FoodOut(SQLModel):
    id: int
    name: str
    source: str
    kcal: float
    protein: float
    fat: float
    carbs: float


def nutrition_for(session: Session, recipes: list[Recipe]) -> dict[int, dict]:
    """recipe_nutrition() for many recipes with two queries."""
    ids = [r.id for r in recipes]
    ings = session.exec(select(Ingredient).where(col(Ingredient.recipe_id).in_(ids))).all()
    foods = {f.id: f for f in session.exec(select(Food).where(col(Food.id).in_({i.food_id for i in ings if i.food_id})))}
    by_recipe: dict[int, list[Ingredient]] = {}
    for i in ings:
        by_recipe.setdefault(i.recipe_id, []).append(i)
    return {r.id: recipe_nutrition(by_recipe.get(r.id, []), foods, r.servings) for r in recipes}


def visible(user: User):
    """Your own recipes plus the shared Cook Well library."""
    return or_(Recipe.owner_id == user.id, Recipe.source == SHARED_SOURCE)


def owned_recipe(session: Session, recipe_id: int, user: User, edit: bool = False) -> Recipe:
    """A recipe the user may see (or, with edit=True, change); 404 otherwise."""
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or not can_see(recipe, user):
        raise HTTPException(404, "recipe not found")
    if edit and not can_edit(recipe, user):
        raise HTTPException(403, "only the owner can edit this recipe")
    return recipe


def can_see(recipe: Recipe, user: User) -> bool:
    return recipe.owner_id == user.id or recipe.source == SHARED_SOURCE


def can_edit(recipe: Recipe, user: User) -> bool:
    return recipe.owner_id == user.id


def recipe_detail(session: Session, recipe: Recipe, user: User) -> RecipeDetail:
    ingredients = session.exec(select(Ingredient).where(Ingredient.recipe_id == recipe.id).order_by(Ingredient.position)).all()
    steps = session.exec(select(Step).where(Step.recipe_id == recipe.id).order_by(Step.position)).all()
    foods = {f.id: f for f in session.exec(select(Food).where(col(Food.id).in_({i.food_id for i in ingredients if i.food_id})))}
    out = [IngredientOut(**i.model_dump(), food_name=foods[i.food_id].name if i.food_id else None,
                         nutrition=macros(i.grams, foods.get(i.food_id))) for i in ingredients]
    from ..models import Favorite, Folder, FolderRecipe
    parent = session.get(Recipe, recipe.parent_id) if recipe.parent_id else None
    extra = dict(
        favorite=session.exec(select(Favorite).where(Favorite.owner_id == user.id, Favorite.recipe_id == recipe.id)).first() is not None,
        folder_ids=list(session.exec(select(FolderRecipe.folder_id).join(Folder, Folder.id == FolderRecipe.folder_id)
                                     .where(Folder.owner_id == user.id, FolderRecipe.recipe_id == recipe.id))),
        parent_title=parent.title if parent and can_see(parent, user) else None,
        variations=[{"id": v.id, "title": v.title} for v in session.exec(
            select(Recipe).where(Recipe.parent_id == recipe.id, Recipe.owner_id == user.id))],
    )
    return RecipeDetail(**recipe.model_dump(), **extra, can_edit=can_edit(recipe, user), ingredients=out, steps=steps,
                        nutrition=Nutrition(**recipe_nutrition(ingredients, foods, recipe.servings)))


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
    if max_minutes:
        stmt = stmt.where(col(Recipe.total_minutes).is_not(None), Recipe.total_minutes <= max_minutes)
    recipes = list(session.exec(stmt.order_by(Recipe.title)))
    if tag := [t for t in tag if t]:
        # OR within a tag family, AND across families.
        groups: dict[str, set[str]] = {}
        for t in tag:
            groups.setdefault(GROUP_OF.get(t.lower(), t.lower()), set()).add(t.lower())
        recipes = [r for r in recipes if all({x.lower() for x in r.tags} & want for want in groups.values())]
    nutrition = nutrition_for(session, recipes)

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
    """Set an ingredient's weight and/or food by hand. Returns the updated recipe."""
    ing = session.get(Ingredient, ingredient_id)
    if ing is None:
        raise HTTPException(404, "ingredient not found")
    recipe = owned_recipe(session, ing.recipe_id, user, edit=True)
    fields = patch.model_dump(exclude_unset=True)
    if "food_id" in fields:
        if fields["food_id"] is not None and session.get(Food, fields["food_id"]) is None:
            raise HTTPException(400, "unknown food")
        ing.food_id = fields["food_id"]
    if "grams" in fields:
        ing.grams = fields["grams"]
        ing.grams_source = "manual" if fields["grams"] is not None else None
    session.add(ing)
    session.commit()
    return recipe_detail(session, recipe, user)


@router.get("/foods", response_model=list[FoodOut])
def search_foods(q: str, limit: int = 25, session: Session = Depends(get_session)):
    """Foods whose name contains every word of q; shortest names first."""
    stmt = select(Food)
    for word in q.split():
        stmt = stmt.where(col(Food.name).ilike(f"%{word}%"))
    foods = session.exec(stmt).all()
    rank = {"custom": 0, "usda_sr_legacy": 1, "usda_foundation": 2}
    foods.sort(key=lambda f: (f.kcal == 0 and "water" not in f.name.lower() and "salt" not in f.name.lower(),
                              rank.get(f.source, 3), len(f.name)))
    return foods[:limit]


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

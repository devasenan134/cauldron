from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, or_, select

from ..db import get_session
from ..deps import current_user
from ..models import Food, Ingredient, Recipe, RecipeBase, Step, User
from ..nutrition import macros, recipe_nutrition

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
    return RecipeDetail(**recipe.model_dump(), can_edit=can_edit(recipe, user), ingredients=out, steps=steps,
                        nutrition=Nutrition(**recipe_nutrition(ingredients, foods, recipe.servings)))


@router.get("/recipes", response_model=list[RecipeSummary])
def list_recipes(q: str | None = None, cuisine: str | None = None, category: str | None = None,
                 session: Session = Depends(get_session), user: User = Depends(current_user)):
    stmt = select(Recipe).where(visible(user))
    if q:
        like = f"%{q}%"
        in_ingredients = select(Ingredient.recipe_id).where(col(Ingredient.name).ilike(like))
        stmt = stmt.where(or_(col(Recipe.title).ilike(like), col(Recipe.id).in_(in_ingredients)))
    if cuisine:
        stmt = stmt.where(Recipe.cuisine == cuisine)
    if category:
        stmt = stmt.where(Recipe.category == category)
    recipes = session.exec(stmt.order_by(Recipe.title)).all()
    nutrition = nutrition_for(session, recipes)
    return [RecipeSummary(**r.model_dump(), kcal_per_serving=(nutrition[r.id]["per_serving"] or {}).get("kcal"))
            for r in recipes]


@router.get("/recipes/facets")
def recipe_facets(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Cuisines and categories in use, for filters."""
    def distinct(column):
        return sorted(v for v in session.exec(select(column).where(visible(user)).distinct()) if v)
    return {"cuisines": distinct(Recipe.cuisine), "categories": distinct(Recipe.category)}


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

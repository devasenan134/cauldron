"""Link ingredients to foods (via foodmap) and resolve their gram weights."""
from sqlmodel import Session, select

from .foodmap import CUSTOM, FOOD_FOR
from .models import Food, Ingredient, Recipe
from .nutrition import grams_per_part, mark_alternatives, resolve_grams


def ensure_custom_foods(session: Session) -> None:
    have = {f.name for f in session.exec(select(Food).where(Food.source == "custom"))}
    for name, (kcal, protein, fat, carbs) in CUSTOM.items():
        if name not in have:
            session.add(Food(name=name, source="custom", kcal=kcal, protein=protein, fat=fat, carbs=carbs))
    session.flush()


def food_index(session: Session) -> dict[str, Food]:
    """Food name -> Food, preferring custom, then SR Legacy (it has portions)."""
    rank = {"usda_foundation": 0, "usda_sr_legacy": 1, "custom": 2}
    index: dict[str, Food] = {}
    for f in session.exec(select(Food).where(Food.owner_id == None)):  # noqa: E711  (not users' own foods)
        cur = index.get(f.name)
        if cur is None or (rank[f.source], f.kcal > 0) > (rank[cur.source], cur.kcal > 0):
            index[f.name] = f
    return index


def link_recipe(session: Session, recipe_id: int, index: dict[str, Food], by_id: dict[int, Food]) -> None:
    ings = session.exec(select(Ingredient).where(Ingredient.recipe_id == recipe_id)).all()
    for ing in ings:
        if ing.food_id is None and ing.prep_id is None and (target := FOOD_FOR.get(ing.name.lower().strip())):
            ing.food_id = index[target].id
    servings = session.get(Recipe, recipe_id).servings
    per_part = grams_per_part(ings, servings)
    for ing in ings:
        if ing.prep_id:
            continue  # a prep's weight is worked out from the prep (recipe_edit.resolve_weights)
        food = by_id.get(ing.food_id) if ing.food_id else None
        ing.grams, ing.grams_source = resolve_grams(ing, food, per_part.get(ing.group), servings)
    mark_alternatives(ings)
    for ing in ings:
        session.add(ing)


def link_all(session: Session) -> None:
    ensure_custom_foods(session)
    index = food_index(session)
    by_id = {f.id: f for f in session.exec(select(Food))}
    for recipe_id in session.exec(select(Ingredient.recipe_id).distinct()).all():
        link_recipe(session, recipe_id, index, by_id)

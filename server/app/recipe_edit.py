"""Recipes people write themselves: save them in the same shape as the Cook Well ones.

Each ingredient is written as a name plus a quantity as you'd say it ("200 g", "2 cloves",
"1 tbsp", "a drizzle"). Saving finds a food for it (the hand-checked Cook Well map first, then the
best-named USDA food) and works out its weight, so the recipe gets calories straight away. Fixes
made by hand on the recipe page (a weight or a food) survive later edits of the same ingredient.
"""
import re

from sqlmodel import Session, SQLModel, col, delete, select

from .foodlink import ensure_custom_foods, food_index
from .foodmap import FOOD_FOR
from .models import Food, Ingredient, Recipe, Step, now
from .nutrition import VAGUE, grams_per_part, mark_alternatives, resolve_grams


class IngredientIn(SQLModel):
    group: str | None = None  # component, e.g. "Sauce"
    name: str
    note: str = ""  # "thinly sliced"
    label: str = ""  # quantity as written: "200 g", "2 cloves"


class StepIn(SQLModel):
    title: str = ""
    text: str


class RecipeIn(SQLModel):
    title: str
    description: str = ""
    image_url: str | None = None
    video_url: str | None = None
    source_url: str | None = None
    servings: float | None = None
    yield_text: str | None = None
    total_minutes: int | None = None
    cuisine: str | None = None
    category: str | None = None
    tags: list[str] = []
    notes: str = ""
    ingredients: list[IngredientIn] = []
    steps: list[StepIn] = []


def parse_quantity(label: str) -> tuple[float | None, str | None]:
    """'2 cloves' -> (2, 'clove'), '1/2 cup' -> (0.5, 'cup'), 'a drizzle' -> (None, 'a drizzle')."""
    text = label.strip().lower()
    if text in VAGUE:
        return None, text
    m = re.match(r"~?\s*(\d+(?:\.\d+)?(?:/\d+)?)(?:\s*(?:-|to)\s*\d+(?:\.\d+)?)?\s*([a-z]*)", text)
    if not m:
        return None, None
    a = m.group(1)
    amount = float(a.split("/")[0]) / float(a.split("/")[1]) if "/" in a else float(a)
    unit = m.group(2) or None
    return amount, (unit.rstrip("s") if unit and unit not in ("s",) and len(unit) > 3 else unit)


def guess_food(session: Session, name: str, index: dict[str, Food]) -> Food | None:
    """The hand-checked Cook Well mapping if it knows the name, else the best-named food."""
    key = name.lower().strip()
    if (target := FOOD_FOR.get(key)) and target in index:
        return index[target]
    words = [w for w in re.findall(r"[a-z]+", key) if len(w) > 2]
    if not words:
        return None
    stmt = select(Food)
    for w in words[:3]:
        stmt = stmt.where(col(Food.name).ilike(f"%{w.rstrip('s')}%"))
    foods = session.exec(stmt.limit(200)).all()
    if not foods and len(words) > 1:  # "boneless chicken thighs" -> try the last word
        foods = session.exec(select(Food).where(col(Food.name).ilike(f"%{words[-1].rstrip('s')}%")).limit(200)).all()
    rank = {"custom": 0, "usda_sr_legacy": 1, "usda_foundation": 2}
    foods.sort(key=lambda f: (not f.name.lower().startswith(words[0][:4]), ", raw" not in f.name.lower(),
                              rank.get(f.source, 3), len(f.name)))
    return foods[0] if foods else None


def save_recipe(session: Session, recipe: Recipe, body: RecipeIn) -> Recipe:
    """Write body into recipe (new or existing), replacing its ingredients and steps."""
    fields = body.model_dump(exclude={"ingredients", "steps"})
    fields["title"] = fields["title"].strip()
    fields["tags"] = [t.strip() for t in body.tags if t.strip()]
    for k, v in fields.items():
        setattr(recipe, k, v)
    recipe.slug = re.sub(r"[^a-z0-9]+", "-", recipe.title.lower()).strip("-")
    recipe.updated_at = now()
    session.add(recipe)
    session.flush()

    # Keep hand fixes for ingredients that are still there (same name and quantity).
    old = {(i.name.lower(), i.label.lower()): i for i in session.exec(select(Ingredient).where(Ingredient.recipe_id == recipe.id))}
    session.exec(delete(Ingredient).where(Ingredient.recipe_id == recipe.id))
    session.exec(delete(Step).where(Step.recipe_id == recipe.id))

    ensure_custom_foods(session)
    index = food_index(session)
    ings = []
    for pos, i in enumerate(x for x in body.ingredients if x.name.strip()):
        amount, unit = parse_quantity(i.label)
        ing = Ingredient(recipe_id=recipe.id, position=pos, group=(i.group or "").strip() or None, name=i.name.strip(),
                         note=i.note.strip(), label=i.label.strip(), amount=amount, unit=unit)
        prev = old.get((ing.name.lower(), ing.label.lower()))
        if prev:
            # Unchanged ingredient: keep how it was read (a copied library recipe keeps its exact weights).
            ing.amount, ing.unit = prev.amount, prev.unit
            if prev.grams_source in ("manual", "given"):
                ing.grams, ing.grams_source = prev.grams, prev.grams_source
        ing.food_id = prev.food_id if prev and prev.food_id else (f.id if (f := guess_food(session, ing.name, index)) else None)
        ing.aisle = prev.aisle if prev else None
        ings.append(ing)
    by_id = {f.id: f for f in session.exec(select(Food).where(col(Food.id).in_({i.food_id for i in ings if i.food_id})))}
    per_part = grams_per_part(ings, recipe.servings)
    for ing in ings:
        ing.aisle = ing.aisle or aisle_for(by_id.get(ing.food_id))
        ing.grams, ing.grams_source = resolve_grams(ing, by_id.get(ing.food_id), per_part.get(ing.group), recipe.servings)
    mark_alternatives(ings)
    for ing in ings:
        session.add(ing)
    for pos, s in enumerate(x for x in body.steps if x.text.strip()):
        session.add(Step(recipe_id=recipe.id, position=pos, title=s.title.strip(), text=s.text.strip()))
    session.commit()
    session.refresh(recipe)
    return recipe


# USDA food categories -> the grocery list's aisles.
AISLES = [("vegetable", "Produce"), ("fruit", "Produce"), ("spice", "Spices"), ("herb", "Produce"),
          ("poultry", "Meat & Seafood"), ("beef", "Meat & Seafood"), ("pork", "Meat & Seafood"),
          ("lamb", "Meat & Seafood"), ("sausage", "Meat & Seafood"), ("finfish", "Meat & Seafood"),
          ("dairy", "Dairy & Eggs"), ("egg", "Dairy & Eggs"), ("baked", "Bakery"), ("cereal grain", "Pantry"),
          ("legume", "Pantry"), ("nut", "Pantry"), ("fats and oils", "Pantry"), ("soup", "Pantry"),
          ("sweets", "Pantry"), ("beverage", "Beverages")]


def aisle_for(food: Food | None) -> str | None:
    cat = (food.category or "").lower() if food else ""
    return next((aisle for key, aisle in AISLES if key in cat), "Other" if food else None)

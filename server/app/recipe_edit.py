"""Recipes people write themselves: save them in the same shape as the Cook Well ones.

Each ingredient is written as a name plus a quantity as you'd say it ("200 g", "2 cloves",
"1 tbsp", "a drizzle"). Saving finds a food for it (the hand-checked Cook Well map first, then the
best-named USDA food) and works out its weight, so the recipe gets calories straight away. Fixes
made by hand on the recipe page (a weight or a food) survive later edits of the same ingredient.

An ingredient named like one of your prepped-ingredient recipes ("pickled onions") uses that prep.
"""
import re

from sqlmodel import Session, SQLModel, col, delete, select

from .foodlink import ensure_custom_foods, food_index
from .foodmap import FOOD_FOR
from .models import Food, Ingredient, Recipe, Step, now
from .nutrition import VAGUE, grams_per_part, mark_alternatives, resolve_grams, resolve_prep_grams


class IngredientIn(SQLModel):
    group: str | None = None  # component, e.g. "Sauce"
    name: str
    note: str = ""  # "thinly sliced"
    label: str = ""  # quantity as written: "200 g", "2 cloves"
    # From imports: a plain grocery name to look the food up by, and a weight guess in grams for
    # amounts we can't weigh ourselves ("1 wedge"). Not stored.
    food_hint: str | None = None
    grams_hint: float | None = None
    prep_id: int | None = None  # made from this prepped-ingredient recipe


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
    is_prep: bool = False
    yield_grams: float | None = None
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


# Words that describe a food without naming it; they don't count when matching names.
FILLER = {"and", "with", "the", "for", "fresh", "large", "small", "medium", "chopped", "diced", "sliced", "minced",
          "boneless", "skinless", "organic", "homemade", "store", "bought", "optional", "plain"}
# Everyday words and what USDA calls them.
SYNONYMS = {"wrap": "tortilla", "passata": "puree", "scallion": "onion", "cilantro": "coriander", "zucchini": "squash",
            "eggplant": "eggplant", "chili": "chili", "chilli": "chili", "yoghurt": "yogurt", "prawn": "shrimp",
            "curd": "yogurt", "dahi": "yogurt"}
# Ready-made versions of a food; only chosen when asked for.
PROCESSED = ("breaded", "battered", "fried", "tenders", "nuggets", "frozen", "prepared", "fast", "restaurant",
             "babyfood", "patties", "sticks", "dehydrated", "mix")


def _stem(w: str) -> str:
    return w[:-3] + "y" if w.endswith("ies") else w[:-2] if w.endswith(("oes", "ches", "shes")) else w.rstrip("s")


def guess_food(session: Session, name: str, index: dict[str, Food], hint: str | None = None,
               owner_id: int | None = None) -> Food | None:
    """One of your own foods by that name, the hand-checked Cook Well mapping, else the best-named food.

    [hint] is a plain grocery name for the same thing ("nonfat evaporated milk" for "evap milk 0%"),
    tried first. Foods are ranked by how many of the words they share, so one odd word ("sieved")
    doesn't send the search off to a different food.
    """
    name = re.sub(r"\bbean\s*curd\b", "tofu", name, flags=re.I)  # not curd (yogurt)
    if owner_id is not None:
        for key in [k.strip() for k in (name, hint) if k and k.strip()]:
            own = session.exec(select(Food).where(Food.owner_id == owner_id, col(Food.base_id).is_(None),
                                                  col(Food.name).ilike(key))).first()
            if own:
                return own
    for key in [k.lower().strip() for k in (name, hint) if k]:
        for variant in (key, key + "s", key.rstrip("s"), key + "es"):
            if (target := FOOD_FOR.get(variant)) and target in index:
                return index[target]
    # "5% fat", "95% lean", "0.2%": a number the food's name should carry too
    pct = [float(m) for m in re.findall(r"(\d+(?:\.\d+)?)\s*%", f"{name} {hint or ''}")]
    if re.fullmatch(r"\s*salt\s*(&|and)\s*(black\s*)?pepper\s*", name.lower()) and "Salt, table" in index:
        return index["Salt, table"]
    for text in [t for t in (hint, name) if t]:
        words = [SYNONYMS.get(_stem(w), _stem(w)) for w in re.findall(r"[a-z]+", text.lower()) if len(w) > 2 and w not in FILLER]
        if not words:
            continue
        # Foods with all the words first; only if there are none, any of them.
        # Foods with all the words first; only if there are none, the foods with each word (a common
        # word like "ground" must not crowd out the rare one, "cumin").
        shared = col(Food.owner_id).is_(None)
        strict = list(session.exec(select(Food).where(shared, *[col(Food.name).ilike(f"%{w}%") for w in words[:5]]).limit(3000)))
        per_word = {w: [] if strict else list(session.exec(select(Food).where(shared, col(Food.name).ilike(f"%{w}%")).limit(1500))) for w in words[:5]}
        candidates = strict or {f.id: f for fs in per_word.values() for f in fs}.values()
        # Rare words say more about the food than common ones ("cumin" vs "ground").
        weight = {w: 1.0 if strict else 1.0 / (1 + len(per_word[w])) ** 0.35 for w in words}
        total_weight = sum(weight.values())
        best, best_score = None, 0.0
        rank = {"custom": 0.3, "usda_sr_legacy": 0.2, "usda_foundation": 0.1}
        for f in candidates:
            fname = f.name.lower()
            fwords = set(_stem(w) for w in re.findall(r"[a-z]+", fname))
            matched = [w for w in words if w in fwords or any(fw.startswith(w) for fw in fwords)]
            if not matched:
                continue
            first = fname.split(",")[0]
            score = sum(weight[w] for w in matched) / total_weight         # share of our words it has
            score += 0.5 * any(w in first for w in words[-1:])            # the main noun leads its name
            score += 0.15 * (", raw" in fname or "plain" in fname)
            score -= 0.03 * max(0, len(fwords) - len(words))              # fewer extra words
            score -= 0.4 * sum(1 for p in PROCESSED if p in fwords and p not in words)
            if pct:
                have = [float(m) for m in re.findall(r"(\d+(?:\.\d+)?)\s*%", fname)]
                if have:
                    score += 0.4 if any(abs(h - p) < 1 or abs(h - (100 - p)) < 1 for h in have for p in pct) else -0.3
            # parts people rarely mean unless they say so ("chicken thigh" isn't its skin)
            score -= 0.6 * sum(1 for p in ("skin", "giblet", "neck", "separable", "gizzard", "liver", "bone") if p in fwords and p not in words)
            score += rank.get(f.source, 0) + (0.1 if f.kcal > 0 else 0)
            if score > best_score:
                best, best_score = f, score
        # need at least the main word to match, or most of the words
        if best is not None and best_score >= 0.6:
            return best
    return None


def save_recipe(session: Session, recipe: Recipe, body: RecipeIn) -> Recipe:
    """Write body into recipe (new or existing), replacing its ingredients and steps."""
    fields = body.model_dump(exclude={"ingredients", "steps"})
    fields["yield_grams"] = body.yield_grams if body.yield_grams and body.yield_grams > 0 else None
    fields["title"] = fields["title"].strip()
    # Without a serving count there's no kcal per serving: a recipe with none makes one.
    fields["servings"] = body.servings if body.servings and body.servings > 0 else 1
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
    preps = visible_preps(session, recipe.owner_id)
    by_title = {_stem(r.title.lower().strip()): r.id for r in preps.values() if r.id != recipe.id}
    ings = []
    hints: dict[int, float] = {}  # ingredient index -> weight guess from an import
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
        # A prep: as chosen, as it was, or (a new ingredient) one of your preps by the same name.
        ing.prep_id = (i.prep_id if i.prep_id in preps and i.prep_id != recipe.id
                       else prev.prep_id if prev else by_title.get(_stem(ing.name.lower())))
        if ing.prep_id:
            ing.food_id = None
        else:
            ing.food_id = prev.food_id if prev and prev.food_id else (
                f.id if (f := guess_food(session, ing.name, index, i.food_hint, recipe.owner_id)) else None)
        ing.aisle = prev.aisle if prev else None
        if i.grams_hint:
            hints[len(ings)] = i.grams_hint
        ings.append(ing)
    resolve_weights(session, recipe, ings)
    # Where we could only guess (or not weigh it at all), an import's own weight guess for that
    # exact amount is better than our typical-serving one.
    for n, grams in hints.items():
        if ings[n].grams_source in (None, "estimate") and ings[n].food_id and grams > 0:
            ings[n].grams, ings[n].grams_source = round(grams, 1), "estimate"
    mark_alternatives(ings)
    for ing in ings:
        session.add(ing)
    for pos, s in enumerate(x for x in body.steps if x.text.strip()):
        session.add(Step(recipe_id=recipe.id, position=pos, title=s.title.strip(), text=s.text.strip()))
    session.commit()
    session.refresh(recipe)
    return recipe


def visible_preps(session: Session, owner_id: int) -> dict[int, Recipe]:
    """Prepped-ingredient recipes this user can use: their own and the shared library's."""
    from .models import User
    from .routes.recipes import visible
    user = session.get(User, owner_id)
    stmt = select(Recipe).where(Recipe.is_prep == True, visible(user) if user else Recipe.owner_id == owner_id)  # noqa: E712
    return {r.id: r for r in session.exec(stmt)}


def resolve_weights(session: Session, recipe: Recipe, ings: list[Ingredient], only: set[int] | None = None) -> None:
    """Work out each ingredient's grams (and aisle): from its food, or for a prep, from the prep.

    With only, just those ingredients (by id); the rest keep what they have.
    """
    from .prep import Kitchen
    by_id = {f.id: f for f in session.exec(select(Food).where(col(Food.id).in_({i.food_id for i in ings if i.food_id})))}
    prep_ids = {i.prep_id for i in ings if i.prep_id}
    kitchen = Kitchen(session, None, list(session.exec(select(Recipe).where(col(Recipe.id).in_(prep_ids))))) if prep_ids else None
    per_part = grams_per_part(ings, recipe.servings)
    for ing in ings:
        if only is not None and ing.id not in only:
            continue
        if ing.prep_id and kitchen and ing.prep_id in kitchen.recipes:
            prep = kitchen.recipes[ing.prep_id]
            full = kitchen.yield_of(prep.id)
            ing.grams, ing.grams_source = resolve_prep_grams(ing, full / prep.servings if full and prep.servings else None)
            continue
        ing.aisle = ing.aisle or aisle_for(by_id.get(ing.food_id))
        ing.grams, ing.grams_source = resolve_grams(ing, by_id.get(ing.food_id), per_part.get(ing.group), recipe.servings)


def relink_recipe(session: Session, recipe: Recipe, only: set[int] | None = None) -> None:
    """Work the weights out again after a food or prep changed (hand-set weights stay)."""
    ings = list(session.exec(select(Ingredient).where(Ingredient.recipe_id == recipe.id)))
    for ing in ings:
        if (only is None or ing.id in only) and ing.grams_source not in ("manual", "given"):
            ing.grams, ing.grams_source = None, None
    resolve_weights(session, recipe, ings, only)
    mark_alternatives(ings)
    for ing in ings:
        session.add(ing)


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

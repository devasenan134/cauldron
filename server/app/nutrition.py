"""Turn ingredient quantities into grams and grams into calories and macros.

Every ingredient gets a gram weight and a note on where it came from:
  given     the recipe states a weight
  parts     "1 part" scaled from gram-weighed parts in the same component
  portion   a count ("2 cloves", "1 onion") times a USDA or typical unit weight
  estimate  a vague amount ("a sprinkle", "a drizzle") set to a typical weight
  manual    typed in by you; never overwritten
Anything else (to taste, for garnish, blank) is left out of the totals and listed.
"""
import re

from .models import Food, Ingredient

MACROS = ("kcal", "protein", "fat", "carbs")

# Vague amounts -> grams (one "unit"; spoonfuls multiply).
VAGUE = {
    "a sprinkle": 1, "a pinch": 0.4, "cranks": 0.1, "a spritz": 1,
    "a spoonful": 15, "spoonful": 15, "spoonfuls": 15,
    "a drizzle": 7, "a splash": 10, "a squeeze": 10, "a handful": 15, "handful": 15,
    "for frying": 15, "for searing": 10, "for stir frying": 10, "for the pan": 7,
    "for toasting": 5, "for cooking": 10, "knob": 10, "a small knob": 5,
}
# Juice or zest yielded by one fruit.
FRUIT_YIELD = {("lemon", "juice"): 30, ("lemon", "zest"): 2, ("lime", "juice"): 20,
               ("lime", "zest"): 1.5, ("orange", "juice"): 85, ("orange", "zest"): 3}
# Typical weight of one item, for foods USDA has no usable portion for.
EACH = {
    # whole produce (specific names before the general ones: the first match wins)
    "green onion": 15, "scallion": 15, "spring onion": 15, "red onion": 150, "onion": 110,
    "cherry tomato": 17, "tomato": 120, "sweet potato": 130, "potato": 200, "carrot": 60,
    "bell pepper": 150, "jalape": 15, "zucchini": 200, "cucumber": 300, "avocado": 150,
    "apple": 180, "banana": 120, "egg": 50,
    "can": 240,  # a standard 15 oz can, drained (beans, chickpeas, tomatoes)
    "brioche": 60, "hoagie": 85, "sub roll": 85, "cubano": 85, "bun": 55, "shokupan": 45,
    "chicken breast": 230, "chicken thigh": 110, "chicken tender": 50, "chipotle": 12,
    "lavash": 60, "naan": 90, "roti": 40, "paratha": 80, "keto flour tortilla": 45,
    "protein tortilla": 60, "shallot": 40, "bay lea": 0.2, "ginger": 10, "lemongrass": 20,
    "clove": 3, "cinnamon stick": 3, "star anise": 1, "chile": 5, "chili": 5,
    "american cheese": 20, "cheese": 20, "bacon": 25, "ramen": 80, "spaghetti": 85,
    "egg yolk": 17, "beef": 450, "chuck": 1300, "lamb": 1000, "venison": 1000, "sausage": 100,
    "wrapper": 8, "lime lea": 0.3, "bunch": 50, "sprig": 1, "stalk": 40, "head": 600,
}
PORTION_PREF = ("medium", "large", "pepper", "tortilla", "muffin", "bagel", "roll", "shell",
                "link", "pita, large", "fruit", "ear", "cucumber", "sweetpotato", "anchovy", "piece",
                "whole", "each", "slice", "leaf", "spear", "bunch")
COUNT_UNITS = {"can", "tin", "ingredient", "clove", "slice", "slices", "stalk", "sprig", "bunch", "head",
               "serving", "a serving", "link"}


def _num(s: str) -> float:
    if "/" in s:
        a, b = s.split("/", 1)
        return float(a) / float(b)
    return float(s)


def count_from_label(label: str, amount: float | None) -> float | None:
    """'4-6 cloves' -> 5, '1/2' -> 0.5, '~3' -> 3; falls back to amount."""
    label = label.strip().lstrip("~").strip()
    m = re.match(r"(\d*\.?\d+(?:/\d+)?)\s*(?:-|to)\s*(\d*\.?\d+(?:/\d+)?)", label)
    if m:
        return (_num(m.group(1)) + _num(m.group(2))) / 2
    m = re.match(r"(\d*\.?\d+(?:/\d+)?)(?!\s*%)", label)
    if m:
        return _num(m.group(1))
    return amount


WEIGHT_UNITS = {"g": 1, "gram": 1, "grams": 1, "kg": 1000, "lb": 453.6, "lbs": 453.6,
                "pound": 453.6, "pounds": 453.6, "oz": 28.35, "ounce": 28.35, "ounces": 28.35}


def weight_in_label(label: str) -> float | None:
    """'2-4 lbs' -> 1360.8, '~900 g' -> 900; None if the label isn't a weight."""
    m = re.match(r"~?\s*[\d./]+(?:\s*(?:-|to)\s*[\d./]+)?\s*([a-z]+)\b", label.lower())
    if not m or m.group(1) not in WEIGHT_UNITS:
        return None
    n = count_from_label(label, None)
    return round(n * WEIGHT_UNITS[m.group(1)], 1) if n else None


# Volume units in millilitres, for recipes written by hand ("2 tbsp", "1 cup").
VOLUME_ML = {"tsp": 5, "teaspoon": 5, "teaspoons": 5, "tbsp": 15, "tablespoon": 15, "tablespoons": 15,
             "cup": 240, "cups": 240, "ml": 1, "l": 1000, "liter": 1000, "litre": 1000, "liters": 1000, "litres": 1000}
# USDA portion names for each volume unit.
VOLUME_NAMES = {5: ("tsp", "teaspoon"), 15: ("tbsp", "tablespoon"), 240: ("cup",)}


def volume_in_label(label: str, food: Food | None) -> tuple[float, str] | None:
    """'2 tbsp' -> grams, using the food's own USDA measure when it has one, else water's density."""
    m = re.match(r"~?\s*[\d./]+(?:\s*(?:-|to)\s*[\d./]+)?\s*([a-z]+)\b", label.lower())
    if not m or m.group(1) not in VOLUME_ML:
        return None
    n = count_from_label(label, None)
    if not n:
        return None
    ml = VOLUME_ML[m.group(1)]
    for p in (food.portions if food else []):
        unit = p["unit"].lower()
        if any(unit.startswith(name) or f" {name}" in unit for name in VOLUME_NAMES.get(ml, ())):
            return round(n * p["grams"], 1), "portion"
    return round(n * ml, 1), "estimate"


def unit_weight(ing: Ingredient, food: Food | None, unit: str) -> float | None:
    name = ing.name.lower()
    portions = food.portions if food else []
    if unit not in ("ingredient", "serving", "a serving"):
        for p in portions:  # exact unit, e.g. clove, slice (not "slice, 1 cup" style measures)
            u = p["unit"].lower()
            if u.startswith(unit.rstrip("s")) and "cup" not in u and p["grams"] <= 80:
                return p["grams"]
    if unit.rstrip("s") == "slice":
        # A slice of something weighed whole ("4-5 slices" of cucumber), not the whole thing.
        whole = next((g for key, g in EACH.items() if re.search(r"\b" + re.escape(key), name)), None)
        if whole is not None and whole > 80:
            return 8
    for key, grams in EACH.items():
        # Match at the start of a word: "can" must not match "American cheese".
        if re.search(r"\b" + re.escape(key), name):
            return grams
    for pref in PORTION_PREF:
        for p in portions:
            if pref in p["unit"].lower() and "cup" not in p["unit"].lower():
                return p["grams"]
    return EACH.get(unit.rstrip("s"))


# Counting words that can stand in for the unit ("3-6 cloves" of garlic).
LABEL_UNITS = {"clove": "clove", "cloves": "clove", "slice": "slice", "slices": "slice", "leaf": "leaf", "leaves": "leaf",
               "sprig": "sprig", "sprigs": "sprig", "stalk": "stalk", "stalks": "stalk", "can": "can", "cans": "can",
               "stick": "stick", "sticks": "stick", "sheet": "sheet", "sheets": "sheet"}
EACH_EXTRA = {"leaf": 0.5, "stick": 113, "sheet": 3}  # basil leaf, butter stick, nori sheet


def resolve_grams(ing: Ingredient, food: Food | None, grams_per_part: float | None,
                  servings: float | None = None) -> tuple[float | None, str | None]:
    unit = (ing.unit or "").lower()
    label = (ing.label or "").strip()
    name = ing.name.lower()
    if unit in ("", "ingredient") and (m := re.match(r"~?\s*[\d./]+(?:\s*(?:-|to)\s*[\d./]+)?\s*([a-z]+)", label.lower())) and m.group(1) in LABEL_UNITS:
        unit = LABEL_UNITS[m.group(1)]
    if ing.grams_source == "manual":
        return ing.grams, "manual"
    if ing.grams is not None and ing.grams_source in (None, "given"):
        return ing.grams, "given"
    if "%" in label:
        return None, None
    if (weight := weight_in_label(label)) is not None:
        return weight, "given"
    if (volume := volume_in_label(label, food)) is not None:
        return volume
    if unit in ("part", "parts") or label.endswith(("part", "parts")):
        n = count_from_label(label, ing.amount)
        return (round(n * grams_per_part, 1), "parts") if n and grams_per_part else (None, None)
    for fruit in ("lemon", "lime", "orange"):
        if unit == fruit or (fruit in label.lower() and fruit in name):
            kind = "zest" if "zest" in name else "juice"
            n = count_from_label(label, ing.amount) or 1
            return round(n * FRUIT_YIELD[(fruit, kind)], 1), "portion"
    if unit in VAGUE:
        n = ing.amount if unit.endswith("spoonfuls") and ing.amount else 1
        return round(n * VAGUE[unit], 1), "estimate"
    if unit in ("serving", "a serving", "servings") and (typical := typical_serving(ing, food)) and typical[0]:
        return round((count_from_label(label, ing.amount) or 1) * typical[0], 1), "estimate"
    if unit in COUNT_UNITS or unit in EACH_EXTRA or (not unit and re.match(r"~?\s*\d", label)):
        n = count_from_label(label, ing.amount)
        each = unit_weight(ing, food, unit or "ingredient") or EACH_EXTRA.get(unit)
        if n and each:
            return round(n * each, 1), "portion"
    # No usable amount (blank, "to taste", "for garnish"): a typical amount per serving, as an estimate.
    if (typical := typical_serving(ing, food)) is not None:
        grams, per_item = typical
        each = unit_weight(ing, food, "ingredient") if per_item else None
        n = servings or 2
        if per_item and each:
            return round(n * each, 1), "estimate"
        if grams:
            return round(n * grams, 1), "estimate"
    return None, None


# Typical grams per serving for toppings and "to taste" amounts (first match wins), and per grocery
# aisle as a fallback. "item" means one piece per serving (a tortilla, a bun).
TYPICAL = [
    ("water", 0), ("ice", 0), ("anchov", 5), ("caper", 3),
    ("salami", 30), ("soppressata", 30), ("mortadella", 30), ("prosciutto", 25), ("pepperoni", 25), ("ham", 40),
    ("capicola", 30), ("parmigiano", 8), ("reggiano", 8), ("pecorino", 8), ("grana", 8), ("salt", 1), ("powder", 1), ("pepper flake", 0.5), ("black pepper", 0.3), ("pepper", 0.3),
    ("tortilla", "item"), ("bun", "item"), ("roll", "item"), ("pita", "item"), ("naan", "item"), ("bread", "item"),
    ("sour cream", 30), ("yogurt", 30), ("crema", 20), ("cream", 15), ("parmesan", 8), ("feta", 20), ("queso", 20),
    ("cheese", 20), ("butter", 7), ("mayo", 10), ("lettuce", 20), ("cabbage", 25), ("slaw", 30), ("pickle", 15),
    ("tomato", 30), ("onion", 15), ("cilantro", 2), ("parsley", 2), ("basil", 2), ("mint", 2), ("dill", 1),
    ("herb", 2), ("scallion", 5), ("green onion", 5), ("chive", 2), ("lime", 5), ("lemon", 5), ("avocado", 35),
    ("guacamole", 30), ("salsa", 30), ("hot sauce", 5), ("sriracha", 5), ("chili crisp", 5), ("sauce", 15),
    ("oil", 5), ("sesame", 2), ("seed", 3), ("nut", 10), ("honey", 7), ("sugar", 4), ("syrup", 7),
    ("gnocchi", 150), ("rice", 75), ("pasta", 85), ("noodle", 85), ("potato", 150), ("sausage", 100), ("egg", 50), ("bacon", 15), ("jalape", 8), ("chile", 3), ("chili", 3),
    ("stock", 60), ("broth", 60), ("vinegar", 5), ("mustard", 5), ("ketchup", 10),
]
TYPICAL_AISLE = {"produce": 30, "dairy": 20, "meat": 100, "seafood": 100, "spice": 1, "condiment": 10,
                 "pantry": 15, "bakery": "item", "bread": "item", "frozen": 50, "canned": 60}


def typical_serving(ing: Ingredient, food: Food | None) -> tuple[float | None, bool] | None:
    """(grams per serving, or None) and whether it's one item per serving."""
    name = ing.name.lower()
    for key, v in TYPICAL:
        if key in name:
            return (None, True) if v == "item" else (float(v), False)
    aisle = (ing.aisle or "").lower()
    for key, v in TYPICAL_AISLE.items():
        if key in aisle:
            return (None, True) if v == "item" else (float(v), False)
    return (10.0, False) if food else None


SPICE_WORDS = ("cumin", "paprika", "turmeric", "powder", "coriander", "cinnamon", "oregano", "thyme", "chili",
               "pepper", "salt", "spice", "masala", "seasoning", "cardamom", "clove", "nutmeg", "sumac", "za'atar",
               "harissa", "fennel", "mustard seed", "sugar", "msg", "dried")


# What a blank-amount ingredient is, to spot alternatives ("sub roll, sesame roll or hoagie").
PROTEIN_WORDS = ("chicken", "beef", "pork", "lamb", "salmon", "shrimp", "fish", "tofu", "tempeh", "steak", "turkey",
                 "chorizo", "sausage", "meat", "carnitas", "bison", "venison", "duck", "paneer", "beans")


def mark_alternatives(ingredients: list[Ingredient]) -> None:
    """Within a component, blank-amount breads (or blank-amount proteins) are options: count the first."""
    seen: set[tuple[str | None, str]] = set()
    for ing in ingredients:
        if ing.grams_source != "estimate" or (ing.label or "").strip():
            continue
        name = ing.name.lower()
        kind = ("bread" if any(k in name for k, v in TYPICAL if v == "item" and k in name) or "roll" in name or "hoagie" in name
                else "protein" if any(w in name for w in PROTEIN_WORDS) else None)
        if kind is None:
            continue
        if (ing.group, kind) in seen:
            ing.grams, ing.grams_source = None, "alternative"
        seen.add((ing.group, kind))


def grams_per_part(ingredients: list[Ingredient], servings: float | None = None) -> dict[str | None, float]:
    """Per component: grams of one 'part', from parts that also state grams.

    With nothing to anchor them, parts are estimated: a spice blend at a teaspoon (3 g) per part,
    anything else so the component comes to about 100 g per serving.
    """
    ratios: dict[str | None, list[float]] = {}
    parts: dict[str | None, list[Ingredient]] = {}
    for ing in ingredients:
        if (ing.unit or "").lower() in ("part", "parts") or (ing.label or "").strip().lower().endswith(("part", "parts")):
            parts.setdefault(ing.group, []).append(ing)
            if ing.grams and ing.amount and ing.grams_source in (None, "given", "manual"):
                ratios.setdefault(ing.group, []).append(ing.grams / ing.amount)
    out = {g: sum(r) / len(r) for g, r in ratios.items()}
    for g, ings in parts.items():
        if g in out:
            continue
        total = sum(count_from_label(i.label or "", i.amount) or 0 for i in ings)
        if not total:
            continue
        group = (g or "").lower()
        if all(any(w in i.name.lower() for w in SPICE_WORDS) for i in ings) or any(w in group for w in ("spice", "rub", "seasoning")):
            out[g] = 3.0
        elif any(w in group for w in ("dressing", "sauce", "marinade", "vinaigrette", "glaze", "drizzle", "dip", "aioli", "mayo")):
            out[g] = round(25 * (servings or 2) / total, 1)  # about 25 g of sauce per serving
        else:
            out[g] = round(100 * (servings or 2) / total, 1)
    return out


def macros(grams: float | None, food) -> dict[str, float] | None:
    """Macros for grams of a food (or anything with per-100 g kcal/protein/fat/carbs, like a prep)."""
    if grams is None or food is None:
        return None
    return {m: round(getattr(food, m) * grams / 100, 1) for m in MACROS}


def source_of(ing: Ingredient, foods: dict[int, Food], preps: dict[int, object] | None = None):
    """What an ingredient's nutrition comes from: its prep recipe, else its food."""
    if ing.prep_id is not None:
        return (preps or {}).get(ing.prep_id)
    return foods.get(ing.food_id)


def recipe_nutrition(ingredients: list[Ingredient], foods: dict[int, Food], servings: float | None,
                     preps: dict[int, object] | None = None) -> dict:
    total = dict.fromkeys(MACROS, 0.0)
    left_out, estimated = [], []
    grams = 0.0
    for ing in ingredients:
        m = macros(ing.grams, source_of(ing, foods, preps))
        if m is None:
            if (ing.food_id is not None or ing.prep_id is not None or ing.grams is not None) and ing.grams_source != "alternative":
                left_out.append(ing.name)
            continue
        for k in MACROS:
            total[k] += m[k]
        grams += ing.grams
        if ing.grams_source == "estimate":
            estimated.append(ing.name)
    # (grams_source "alternative": another option for an ingredient already counted; left out quietly.)
    total = {k: round(v, 1) for k, v in total.items()}
    per_serving = {k: round(v / servings, 1) for k, v in total.items()} if servings else None
    return {"total": total, "per_serving": per_serving, "left_out": left_out, "estimated": estimated,
            "grams": round(grams, 1)}


def prep_yield(recipe, ingredients: list[Ingredient]) -> float | None:
    """What a prep weighs when done: as set on the recipe, else the weight of what goes in."""
    if recipe.yield_grams:
        return recipe.yield_grams
    grams = sum(i.grams for i in ingredients if i.grams and i.grams_source != "alternative")
    return round(grams, 1) or None


PORTION_WORDS = {"serving", "servings", "portion", "portions", "a serving", "a portion"}


def resolve_prep_grams(ing: Ingredient, grams_per_serving: float | None) -> tuple[float | None, str | None]:
    """Grams of a prepped ingredient: a weight, a spoon/cup measure (as water), or servings of the prep."""
    if ing.grams_source == "manual":
        return ing.grams, "manual"
    label = (ing.label or "").strip()
    if (weight := weight_in_label(label)) is not None:
        return weight, "given"
    if (volume := volume_in_label(label, None)) is not None:
        return volume[0], "estimate"
    unit = (ing.unit or "").lower()
    word = re.sub(r"^~?\s*[\d./]+(?:\s*(?:-|to)\s*[\d./]+)?\s*", "", label.lower()).strip()
    if grams_per_serving and (unit in PORTION_WORDS or word in PORTION_WORDS or (label and not word)):
        n = count_from_label(label, ing.amount) or 1
        return round(n * grams_per_serving, 1), "portion"
    return None, None

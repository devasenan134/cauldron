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
COUNT_UNITS = {"ingredient", "clove", "slice", "slices", "stalk", "sprig", "bunch", "head",
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


def unit_weight(ing: Ingredient, food: Food | None, unit: str) -> float | None:
    name = ing.name.lower()
    portions = food.portions if food else []
    if unit not in ("ingredient", "serving", "a serving"):
        for p in portions:  # exact unit, e.g. clove, slice
            if p["unit"].lower().startswith(unit.rstrip("s")):
                return p["grams"]
    for key, grams in EACH.items():
        if key in name:
            return grams
    for pref in PORTION_PREF:
        for p in portions:
            if pref in p["unit"].lower() and "cup" not in p["unit"].lower():
                return p["grams"]
    return EACH.get(unit.rstrip("s"))


def resolve_grams(ing: Ingredient, food: Food | None, grams_per_part: float | None) -> tuple[float | None, str | None]:
    unit = (ing.unit or "").lower()
    label = (ing.label or "").strip()
    name = ing.name.lower()
    if ing.grams_source == "manual":
        return ing.grams, "manual"
    if ing.grams is not None and ing.grams_source in (None, "given"):
        return ing.grams, "given"
    if "%" in label:
        return None, None
    if (weight := weight_in_label(label)) is not None:
        return weight, "given"
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
    if unit in COUNT_UNITS or (not unit and re.match(r"~?\s*\d", label)):
        n = count_from_label(label, ing.amount)
        each = unit_weight(ing, food, unit or "ingredient")
        if n and each:
            return round(n * each, 1), "portion"
    return None, None


def grams_per_part(ingredients: list[Ingredient]) -> dict[str | None, float]:
    """Per component: grams of one 'part', from parts that also state grams."""
    ratios: dict[str | None, list[float]] = {}
    for ing in ingredients:
        if (ing.unit or "").lower() in ("part", "parts") and ing.grams and ing.amount:
            ratios.setdefault(ing.group, []).append(ing.grams / ing.amount)
    return {g: sum(r) / len(r) for g, r in ratios.items()}


def macros(grams: float | None, food: Food | None) -> dict[str, float] | None:
    if grams is None or food is None:
        return None
    return {m: round(getattr(food, m) * grams / 100, 1) for m in MACROS}


def recipe_nutrition(ingredients: list[Ingredient], foods: dict[int, Food], servings: float | None) -> dict:
    total = dict.fromkeys(MACROS, 0.0)
    left_out, estimated = [], []
    for ing in ingredients:
        m = macros(ing.grams, foods.get(ing.food_id))
        if m is None:
            if ing.food_id is not None or ing.grams is not None:
                left_out.append(ing.name)
            continue
        for k in MACROS:
            total[k] += m[k]
        if ing.grams_source == "estimate":
            estimated.append(ing.name)
    total = {k: round(v, 1) for k, v in total.items()}
    per_serving = {k: round(v / servings, 1) for k, v in total.items()} if servings else None
    return {"total": total, "per_serving": per_serving, "left_out": left_out, "estimated": estimated}

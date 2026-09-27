"""Load USDA FoodData Central (Foundation + SR Legacy JSON) into the food table.

Download the two JSON zips from https://fdc.nal.usda.gov/download-datasets into
data/usda/ and unzip them first. Re-running updates foods in place.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sqlmodel import Session, select  # noqa: E402

from app.db import engine, init_db  # noqa: E402
from app.models import Food  # noqa: E402

USDA = Path(__file__).resolve().parents[2] / "data" / "usda"
FIELDS = {1003: "protein", 1004: "fat", 1005: "carbs", 1079: "fiber", 2000: "sugar", 1093: "sodium_mg"}


def nutrients(food: dict) -> dict:
    by_id = {n["nutrient"]["id"]: n.get("amount") for n in food.get("foodNutrients", []) if n.get("amount") is not None}
    out = {field: by_id[nid] for nid, field in FIELDS.items() if nid in by_id}
    # Energy: kcal (1008), else Atwater specific/general (2048/2047), else kJ (1062).
    kcal = next((by_id[i] for i in (1008, 2048, 2047) if i in by_id), None)
    if kcal is None and 1062 in by_id:
        kcal = by_id[1062] / 4.184
    out["kcal"] = round(kcal or 0, 1)
    return out


def portions(food: dict) -> list[dict]:
    out = []
    for p in food.get("foodPortions", []):
        amount = p.get("amount") or p.get("value") or 1
        unit = (p.get("measureUnit") or {}).get("name")
        unit = p.get("modifier") if unit in (None, "undetermined") else unit
        if unit and p.get("gramWeight") and unit != "RACC":
            desc = " ".join(x for x in (unit, p.get("portionDescription") or "") if x).strip()
            out.append({"unit": desc if unit == "undetermined" else unit, "grams": round(p["gramWeight"] / amount, 2),
                        "note": p.get("modifier") if unit != p.get("modifier") else ""})
    return out


def main() -> None:
    init_db()
    sets = [("usda_foundation", "FoundationFoods", "FoodData_Central_foundation_food_json_*.json"),
            ("usda_sr_legacy", "SRLegacyFoods", "FoodData_Central_sr_legacy_food_json_*.json")]
    with Session(engine) as session:
        existing = {f.fdc_id: f for f in session.exec(select(Food).where(Food.fdc_id.is_not(None)))}
        n = 0
        for source, key, pattern in sets:
            path = max(USDA.glob(pattern))
            for raw in filter(None, json.loads(path.read_text())[key]):
                food = existing.get(raw["fdcId"]) or Food(fdc_id=raw["fdcId"], name="", source=source)
                food.name = raw["description"]
                food.source = source
                food.category = (raw.get("foodCategory") or {}).get("description")
                for k, v in nutrients(raw).items():
                    setattr(food, k, v)
                food.portions = portions(raw)
                session.add(food)
                n += 1
        session.commit()
    print(f"loaded {n} USDA foods")


if __name__ == "__main__":
    main()

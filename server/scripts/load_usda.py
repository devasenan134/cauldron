"""Load USDA FoodData Central (Foundation + SR Legacy JSON) into the food table.

The two JSON zips from https://fdc.nal.usda.gov/download-datasets go in data/usda/ (next to the
database); if they aren't there, they're downloaded and unzipped. Re-running updates foods in place.
USDA data is public domain.
"""
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sqlmodel import Session, select  # noqa: E402

from app.db import DB_PATH, engine, init_db  # noqa: E402
from app.models import Food  # noqa: E402

USDA = DB_PATH.parent / "usda"
# The releases this was built against; a newer Foundation release works too (put its zip in data/usda/).
DOWNLOADS = {"foundation": "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip",
             "sr_legacy": "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"}
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


def dataset(name: str) -> Path:
    """The newest FoodData_Central_<name>_food_json_*.json, unzipping or downloading it if needed."""
    pattern = f"FoodData_Central_{name}_food_json_*"
    USDA.mkdir(parents=True, exist_ok=True)
    if not list(USDA.glob(pattern + ".json")):
        zips = sorted(USDA.glob(pattern + ".zip"))
        if not zips:
            url = DOWNLOADS[name]
            print(f"downloading {url}")
            zips = [USDA / url.rsplit("/", 1)[1]]
            urllib.request.urlretrieve(url, zips[0])
        with zipfile.ZipFile(zips[-1]) as z:
            z.extractall(USDA)
    return max(USDA.glob(pattern + ".json"))


def main() -> None:
    init_db()
    sets = [("usda_foundation", "FoundationFoods", "foundation"), ("usda_sr_legacy", "SRLegacyFoods", "sr_legacy")]
    with Session(engine) as session:
        existing = {f.fdc_id: f for f in session.exec(select(Food).where(Food.fdc_id.is_not(None)))}
        n = 0
        for source, key, name in sets:
            path = dataset(name)
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

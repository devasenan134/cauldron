"""Load data/raw/cookwell/*.json into the database. Re-running updates in place."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sqlmodel import Session, delete, select  # noqa: E402

from app.db import engine, init_db  # noqa: E402
from app.models import Ingredient, Recipe, Step, User  # noqa: E402
from app.users import owner  # noqa: E402

RAW = Path(__file__).resolve().parents[2] / "data" / "raw" / "cookwell"
GRAMS_PER = {"gram": 1, "grams": 1, "g": 1, "kilogram": 1000, "kg": 1000,
             "pound": 453.6, "pounds": 453.6, "lb": 453.6, "lbs": 453.6,
             "ounce": 28.35, "ounces": 28.35, "oz": 28.35}


def portable_text(blocks) -> str:
    """Sanity portable text -> plain text, one paragraph per block."""
    paras = []
    for b in blocks or []:
        if b.get("_type") == "block":
            paras.append("".join(c.get("text", "") for c in b.get("children", [])).strip())
    return "\n\n".join(p for p in paras if p)


def to_grams(q: dict | None) -> float | None:
    """Weight in grams from a quantity or its alternative, if either states one."""
    for part in (q, (q or {}).get("alternative")):
        if not part:
            continue
        unit = (part.get("unit") or "").lower()
        if part.get("amount") is not None and unit in GRAMS_PER:
            return round(part["amount"] * GRAMS_PER[unit], 1)
        m = re.fullmatch(r"~?\s*(\d+(?:\.\d+)?)\s*(g|kg|grams?|lbs?|oz)\b.*", (part.get("label") or "").strip(), re.I)
        if m:
            return round(float(m.group(1)) * GRAMS_PER[m.group(2).lower()], 1)
    return None


def first_number(text: str | None) -> float | None:
    m = re.search(r"\d+(?:\.\d+)?", text or "")
    return float(m.group()) if m else None


def minutes(iso: str | None) -> int | None:
    m = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?", iso or "")
    return int(m.group(1) or 0) * 60 + int(m.group(2) or 0) if m and any(m.groups()) else None


def load(session: Session, user: User, raw: dict) -> Recipe:
    meta = raw.get("metadata") or {}
    video = raw.get("videoLink") or {}
    nutrition = (raw.get("nutrition") or {}).get("nutritionValues") or None
    servings = ((raw.get("nutrition") or {}).get("nutritionConfiguration") or {}).get("recommendedServingsAmount")
    url = f"https://www.cookwell.com/recipe/{raw['slug']}"

    recipe = session.exec(select(Recipe).where(Recipe.source_url == url)).first() or Recipe(owner_id=user.id, title="", slug="")
    recipe.title = raw["title"]
    recipe.slug = raw["slug"]
    recipe.source = "cookwell"
    recipe.source_url = url
    recipe.video_url = video.get("contentUrl")
    recipe.image_url = (raw.get("image") or {}).get("src")
    recipe.author = (raw.get("creator") or {}).get("name")
    recipe.description = portable_text(raw.get("preface")) or raw.get("description") or ""
    recipe.yield_text = meta.get("yield")
    recipe.servings = servings or first_number(meta.get("yield"))
    recipe.total_minutes = minutes(meta.get("totalTime"))
    recipe.category = meta.get("category")
    recipe.cuisine = meta.get("cuisine")
    recipe.tags = [t["title"] for t in raw.get("tags") or [] if t and t.get("title")]
    recipe.source_nutrition = nutrition or None
    session.add(recipe)
    session.flush()

    session.exec(delete(Ingredient).where(Ingredient.recipe_id == recipe.id))
    session.exec(delete(Step).where(Step.recipe_id == recipe.id))
    for i, ing in enumerate(raw.get("ingredients") or []):
        q = ing.get("quantity") or {}
        session.add(Ingredient(
            recipe_id=recipe.id, position=i,
            group=(ing.get("component") or {}).get("title"),
            name=ing["title"].strip(), note=ing.get("note") or "",
            amount=q.get("amount"), unit=q.get("unit"), label=q.get("label") or "",
            grams=to_grams(q), aisle=ing.get("groceryAisle"),
        ))
    for i, step in enumerate(raw.get("steps") or []):
        session.add(Step(recipe_id=recipe.id, position=i, title=step.get("title") or "",
                         text=portable_text(step.get("instructions"))))
    return recipe


def main() -> None:
    init_db()
    files = sorted(RAW.glob("*.json"))
    with Session(engine) as session:
        user = owner(session)
        for f in files:
            load(session, user, json.loads(f.read_text()))
        session.commit()
        n_ing = len(session.exec(select(Ingredient)).all())
        n_g = len(session.exec(select(Ingredient).where(Ingredient.grams.is_not(None))).all())
    print(f"imported {len(files)} recipes, {n_ing} ingredients ({n_g} with a gram weight)")


if __name__ == "__main__":
    main()

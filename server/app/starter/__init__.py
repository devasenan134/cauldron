"""The starter recipes: about 120 home recipes written for Cauldron, that every user sees.

They live in recipes.json next to this file (Apache-2.0 like the code; the photos are other
people's, under the licence in each recipe's image_credit). The server adds any that are missing
when it starts, owned by the owner with source "starter", matched by slug. `--refresh` (or
loading USDA) writes them all again from the file, finding their foods afresh.

Each recipe is written like this; ingredients are "quantity | name | note", and a line starting
with "# " starts a component ("# Tempering"):

    {"slug": "sambar", "title": "Sambar", "servings": 4, "total_minutes": 50,
     "category": "Lunch", "cuisine": "South Indian", "tags": ["Vegetarian"],
     "ingredients": ["150 g | toor dal | rinsed", "# Tempering", "1 tbsp | ghee"],
     "steps": ["Cook the dal...", "..."],
     "image": "https://upload.wikimedia.org/...", "image_credit": {"author": ..., "license": ...,
     "license_url": ..., "source_url": ...}}
"""
import hashlib
import json
import logging
import mimetypes
from pathlib import Path

import httpx
from sqlmodel import Session, delete, select

from ..models import Ingredient, Recipe
from ..recipe_edit import IngredientIn, RecipeIn, StepIn, save_recipe
from ..routes.images import IMAGE_DIR
from ..routes.recipes import STARTER_SOURCE
from ..users import owner

log = logging.getLogger(__name__)

FILE = Path(__file__).with_name("recipes.json")
# Wikimedia asks for a User-Agent that says who's asking.
USER_AGENT = "Cauldron/1.0 (https://github.com/devasenan134/cauldron; starter recipe photos)"


def load() -> list[dict]:
    return json.loads(FILE.read_text())


def ingredient(line: str, group: str | None) -> IngredientIn:
    """'200 g | chicken thighs | cut into chunks' -> an ingredient."""
    label, name, note = ([p.strip() for p in line.split("|")] + ["", ""])[:3]
    return IngredientIn(group=group, label=label, name=name, note=note)


def body_of(entry: dict, image_url: str | None) -> RecipeIn:
    ings, group = [], None
    for line in entry.get("ingredients", []):
        if line.startswith("# "):
            group = line[2:].strip() or None
        else:
            ings.append(ingredient(line, group))
    return RecipeIn(
        title=entry["title"], description=entry.get("description", ""), image_url=image_url,
        servings=entry.get("servings"), yield_text=entry.get("yield_text"), total_minutes=entry.get("total_minutes"),
        cuisine=entry.get("cuisine"), category=entry.get("category"), tags=entry.get("tags", []), notes=entry.get("notes", ""),
        is_prep=entry.get("is_prep", False), yield_grams=entry.get("yield_grams"), ingredients=ings,
        steps=[StepIn(text=s) for s in entry.get("steps", [])],
    )


def photo_stem(slug: str, url: str) -> str:
    """The file name a photo is kept under here; a different photo gets a different name."""
    return f"starter-{slug}-{hashlib.sha256(url.encode()).hexdigest()[:8]}"


def local_photo(slug: str, url: str) -> str | None:
    """The photo at url, if it has been downloaded to this server (data/images/)."""
    found = sorted(IMAGE_DIR.glob(photo_stem(slug, url) + ".*")) if IMAGE_DIR.is_dir() else []
    return f"/api/images/{found[0].name}" if found else None


def seed(session: Session, refresh: bool = False) -> int:
    """Add the starter recipes that are missing (all of them, with refresh). Returns how many were written."""
    entries = load()
    existing = {r.slug: r for r in session.exec(select(Recipe).where(Recipe.source == STARTER_SOURCE))}
    if not refresh and all(e["slug"] in existing for e in entries):
        return 0
    owner_id = owner(session).id
    # Starter recipes may only use starter preps (never someone's private one of the same name);
    # preps go first so the dishes that use them find them.
    preps = {r.id: r for r in existing.values() if r.is_prep}
    written = 0
    for entry in sorted(entries, key=lambda e: not e.get("is_prep")):
        slug = entry["slug"]
        recipe = existing.get(slug)
        if recipe is not None and not refresh:
            continue
        if recipe is None:
            recipe = Recipe(owner_id=owner_id, source=STARTER_SOURCE, title=entry["title"], slug=slug)
        else:
            # Written again from the file: forget the old ingredients so foods are looked up afresh.
            session.exec(delete(Ingredient).where(Ingredient.recipe_id == recipe.id))
        remote = entry.get("image")
        recipe = save_recipe(session, recipe, body_of(entry, (local_photo(slug, remote) if remote else None) or remote), preps=preps)
        recipe.slug, recipe.source, recipe.author = slug, STARTER_SOURCE, "Cauldron"
        recipe.image_credit = entry.get("image_credit") if recipe.image_url else None
        session.add(recipe)
        session.commit()
        if recipe.is_prep:
            preps[recipe.id] = recipe
        written += 1
    return written


def fetch_photos(session: Session) -> int:
    """Download starter photos still linked from elsewhere, so pages load them from this server."""
    todo = [r for r in session.exec(select(Recipe).where(Recipe.source == STARTER_SOURCE)) if (r.image_url or "").startswith("http")]
    done = 0
    with httpx.Client(headers={"User-Agent": USER_AGENT}, timeout=30, follow_redirects=True) as http:
        for recipe in todo:
            try:
                r = http.get(recipe.image_url)
                r.raise_for_status()
                kind = r.headers.get("content-type", "").split(";")[0]
                if not kind.startswith("image/"):
                    continue
                IMAGE_DIR.mkdir(parents=True, exist_ok=True)
                name = photo_stem(recipe.slug, recipe.image_url) + (mimetypes.guess_extension(kind) or ".jpg")
                (IMAGE_DIR / name).write_bytes(r.content)
            except httpx.HTTPError as e:
                log.warning("starter photo for %s: %s", recipe.slug, e)
                continue
            recipe.image_url = f"/api/images/{name}"
            session.add(recipe)
            session.commit()
            done += 1
    return done

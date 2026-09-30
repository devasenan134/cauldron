"""The owner's recipe libraries (Settings → Recipe libraries).

Two libraries: "everyone" (the starter recipes every user sees) and "guests" (the Cook Well
recipes only the owner and CAULDRON_ALLOWED_EMAILS see). The owner can hide a recipe (nobody sees
it; it's kept, so seeding doesn't add it back), show it again, put one of their own recipes in a
library, and take it back out. Editing uses the normal recipe editor.

Cook Well's recipes are someone else's work: they never go in the "everyone" library, and neither
do copies of them or imported recipes.
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, select

from ..db import get_session
from ..deps import current_user, is_owner
from ..models import Recipe, User
from .catalog import summaries
from .recipes import LIBRARY_SOURCES, SHARED_SOURCE, STARTER_SOURCE, RecipeSummary


def owner_only(user: User = Depends(current_user)) -> User:
    if not is_owner(user):
        raise HTTPException(403, "only the owner can manage the recipe libraries")
    return user


router = APIRouter(prefix="/admin/libraries", dependencies=[Depends(owner_only)])

LIBRARIES = {"everyone": STARTER_SOURCE, "guests": SHARED_SOURCE}


class LibraryRecipe(RecipeSummary):
    hidden: bool
    edited: bool  # a starter recipe changed in the app (recipes.json no longer rewrites it)
    yours: bool  # one of your recipes you put here: it can go back to your recipes


class HiddenIn(SQLModel):
    hidden: bool


def library_source(name: str) -> str:
    if name not in LIBRARIES:
        raise HTTPException(404, "no such library")
    return LIBRARIES[name]


def library_recipe(session: Session, recipe_id: int) -> Recipe:
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or recipe.source not in LIBRARY_SOURCES:
        raise HTTPException(404, "that recipe isn't in a library")
    return recipe


def items(session: Session, user: User, recipes: list[Recipe]) -> list[LibraryRecipe]:
    by_id = {r.id: r for r in recipes}
    return [LibraryRecipe(**s.model_dump(), hidden=by_id[s.id].hidden, edited=by_id[s.id].edited,
                          yours=by_id[s.id].library_from is not None) for s in summaries(session, recipes, user)]


@router.get("/{name}", response_model=list[LibraryRecipe])
def library(name: str, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Every recipe in the library, hidden ones too."""
    source = library_source(name)
    return items(session, user, list(session.exec(select(Recipe).where(Recipe.source == source).order_by(Recipe.title))))


@router.patch("/recipes/{recipe_id}", response_model=LibraryRecipe)
def set_hidden(recipe_id: int, body: HiddenIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    recipe = library_recipe(session, recipe_id)
    recipe.hidden = body.hidden
    session.add(recipe)
    session.commit()
    return items(session, user, [recipe])[0]


def copied_from_cook_well(session: Session, recipe: Recipe) -> bool:
    """A version of a Cook Well recipe (or of a version of one)."""
    seen = set()
    while recipe.parent_id and recipe.parent_id not in seen:
        seen.add(recipe.parent_id)
        recipe = session.get(Recipe, recipe.parent_id)
        if recipe is None:
            return False
        if recipe.source == SHARED_SOURCE:
            return True
    return False


@router.post("/{name}/recipes/{recipe_id}", response_model=LibraryRecipe)
def add(name: str, recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Put one of your own recipes in a library. It leaves your recipes until you take it back."""
    source = library_source(name)
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or recipe.owner_id != user.id or recipe.source in LIBRARY_SOURCES:
        raise HTTPException(400, "only your own recipes can be put in a library")
    if source == STARTER_SOURCE:
        if recipe.source != "manual":
            raise HTTPException(400, "Imported recipes are someone else's work, so they can't go in the library everyone sees.")
        if copied_from_cook_well(session, recipe):
            raise HTTPException(400, "That's a version of a Cook Well recipe, so it can't go in the library everyone sees.")
    recipe.library_from, recipe.source, recipe.hidden = recipe.source, source, False
    # A slug of its own, so it can't be mistaken for a recipe in recipes.json.
    recipe.slug = f"{recipe.slug}-{recipe.id}"
    session.add(recipe)
    session.commit()
    return items(session, user, [recipe])[0]


@router.delete("/recipes/{recipe_id}")
def take_back(recipe_id: int, session: Session = Depends(get_session)):
    """A recipe you put in a library goes back to your own recipes (only you see it again)."""
    recipe = library_recipe(session, recipe_id)
    if recipe.library_from is None:
        raise HTTPException(400, "this recipe came with the library: hide it instead")
    recipe.source, recipe.library_from, recipe.hidden = recipe.library_from, None, False
    recipe.slug = recipe.slug.removesuffix(f"-{recipe.id}")
    session.add(recipe)
    session.commit()
    return {"ok": True}

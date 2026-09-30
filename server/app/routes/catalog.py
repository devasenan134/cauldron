"""Your profile: what you've cooked, and your catalog (your recipes, favorites and folders)."""
from collections import Counter
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, delete, select

from ..db import get_session
from ..deps import current_user
from ..models import Favorite, Folder, FolderRecipe, Ingredient, PlanEntry, Recipe, Step, User
from ..recipe_edit import RecipeIn, save_recipe
from .recipes import RecipeSummary, not_library, nutrition_for, owned_recipe

router = APIRouter()


def summaries(session: Session, recipes: list[Recipe], user: User) -> list[RecipeSummary]:
    nutrition = nutrition_for(session, recipes, user)
    return [RecipeSummary(**r.model_dump(), kcal_per_serving=(nutrition[r.id]["per_serving"] or {}).get("kcal")) for r in recipes]


def cooked_entries(session: Session, user: User, since: date | None = None) -> list[PlanEntry]:
    """Meals you cooked: recipes on your plan on days up to today (leftovers and notes aren't cooking)."""
    stmt = select(PlanEntry).where(PlanEntry.owner_id == user.id, col(PlanEntry.recipe_id).is_not(None),
                                   col(PlanEntry.leftover_of).is_(None), col(PlanEntry.day).is_not(None), PlanEntry.day <= date.today())
    if since:
        stmt = stmt.where(PlanEntry.day >= since)
    return list(session.exec(stmt.order_by(col(PlanEntry.day).desc(), PlanEntry.position)))


# --- profile

class ProfileOut(SQLModel):
    cooked: int  # meals cooked, all time
    recipes_cooked: int  # different recipes
    mine: int  # your own recipes and variations
    favorites: int
    streak: int  # days in a row with something cooked, up to today (or yesterday)
    days: dict[date, int]  # meals cooked per day, last 20 weeks (for the calendar)
    out_days: list[date] = []  # days you logged eating out, last 20 weeks (red on the calendar)
    cuisines: list[tuple[str, int]]  # most cooked lately
    categories: list[tuple[str, int]]


@router.get("/profile", response_model=ProfileOut)
def profile(session: Session = Depends(get_session), user: User = Depends(current_user)):
    all_cooked = cooked_entries(session, user)
    since = date.today() - timedelta(weeks=20)
    per_day = Counter(e.day for e in all_cooked if e.day >= since)
    streak, day = 0, date.today()
    if day not in per_day:  # today isn't over yet
        day -= timedelta(days=1)
    while day in per_day or (day >= since and any(e.day == day for e in all_cooked)):
        streak += 1
        day -= timedelta(days=1)
    recent_ids = [e.recipe_id for e in all_cooked if e.day >= date.today() - timedelta(days=90)]
    recipes = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_(set(recent_ids))))}
    cuisines = Counter(recipes[i].cuisine for i in recent_ids if i in recipes and recipes[i].cuisine)
    categories = Counter(recipes[i].category for i in recent_ids if i in recipes and recipes[i].category)
    mine = len(session.exec(select(Recipe.id).where(Recipe.owner_id == user.id, not_library())).all())
    favorites = len(session.exec(select(Favorite.id).where(Favorite.owner_id == user.id)).all())
    out_days = sorted(set(session.exec(select(PlanEntry.day).where(
        PlanEntry.owner_id == user.id, PlanEntry.status == "out", PlanEntry.day >= since))))
    return ProfileOut(cooked=len(all_cooked), recipes_cooked=len({e.recipe_id for e in all_cooked}), mine=mine, favorites=favorites,
                      streak=streak, days=dict(per_day), out_days=out_days, cuisines=cuisines.most_common(5), categories=categories.most_common(5))


class CookedOut(SQLModel):
    day: date
    entry_id: int
    servings: float
    batch: float | None  # portions cooked, if it was a batch
    recipe: RecipeSummary


@router.get("/cooked", response_model=list[CookedOut])
def cooked(limit: int = 200, session: Session = Depends(get_session), user: User = Depends(current_user)):
    entries = cooked_entries(session, user)[:limit]
    recipes = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_({e.recipe_id for e in entries})))}
    by_id = {s.id: s for s in summaries(session, list(recipes.values()), user)}
    return [CookedOut(day=e.day, entry_id=e.id, servings=e.servings, batch=e.cook_portions, recipe=by_id[e.recipe_id])
            for e in entries if e.recipe_id in by_id]


# --- catalog

class FolderOut(SQLModel):
    id: int
    name: str
    count: int
    covers: list[str]  # up to four photos


class CatalogOut(SQLModel):
    mine: list[RecipeSummary]
    favorites: list[RecipeSummary]
    folders: list[FolderOut]


def folder_out(session: Session, folder: Folder) -> FolderOut:
    rows = session.exec(select(Recipe).join(FolderRecipe, FolderRecipe.recipe_id == Recipe.id)
                        .where(FolderRecipe.folder_id == folder.id, Recipe.hidden == False).order_by(col(FolderRecipe.added_at).desc())).all()  # noqa: E712
    return FolderOut(id=folder.id, name=folder.name, count=len(rows), covers=[r.image_url for r in rows if r.image_url][:4])


@router.get("/catalog", response_model=CatalogOut)
def catalog(session: Session = Depends(get_session), user: User = Depends(current_user)):
    mine = list(session.exec(select(Recipe).where(Recipe.owner_id == user.id, not_library())
                             .order_by(col(Recipe.updated_at).desc())))
    favs = list(session.exec(select(Recipe).join(Favorite, Favorite.recipe_id == Recipe.id)
                             .where(Favorite.owner_id == user.id, Recipe.hidden == False)  # noqa: E712
                             .order_by(col(Favorite.created_at).desc())))
    folders = session.exec(select(Folder).where(Folder.owner_id == user.id).order_by(Folder.name)).all()
    return CatalogOut(mine=summaries(session, mine, user), favorites=summaries(session, favs, user), folders=[folder_out(session, f) for f in folders])


@router.put("/favorites/{recipe_id}")
def add_favorite(recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    owned_recipe(session, recipe_id, user)
    if not session.exec(select(Favorite).where(Favorite.owner_id == user.id, Favorite.recipe_id == recipe_id)).first():
        session.add(Favorite(owner_id=user.id, recipe_id=recipe_id))
        session.commit()
    return {"ok": True}


@router.delete("/favorites/{recipe_id}")
def remove_favorite(recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    session.exec(delete(Favorite).where(Favorite.owner_id == user.id, Favorite.recipe_id == recipe_id))
    session.commit()
    return {"ok": True}


class FolderIn(SQLModel):
    name: str


def owned_folder(session: Session, folder_id: int, user: User) -> Folder:
    folder = session.get(Folder, folder_id)
    if folder is None or folder.owner_id != user.id:
        raise HTTPException(404, "folder not found")
    return folder


@router.post("/folders", response_model=FolderOut)
def create_folder(body: FolderIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    if not body.name.strip():
        raise HTTPException(400, "a folder needs a name")
    folder = Folder(owner_id=user.id, name=body.name.strip())
    session.add(folder)
    session.commit()
    session.refresh(folder)
    return folder_out(session, folder)


@router.patch("/folders/{folder_id}", response_model=FolderOut)
def rename_folder(folder_id: int, body: FolderIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    folder = owned_folder(session, folder_id, user)
    if body.name.strip():
        folder.name = body.name.strip()
        session.add(folder)
        session.commit()
    return folder_out(session, folder)


@router.delete("/folders/{folder_id}")
def delete_folder(folder_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Deletes the folder only; its recipes stay."""
    session.delete(owned_folder(session, folder_id, user))
    session.commit()
    return {"ok": True}


class FolderDetail(SQLModel):
    id: int
    name: str
    recipes: list[RecipeSummary]


@router.get("/folders/{folder_id}", response_model=FolderDetail)
def get_folder(folder_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    folder = owned_folder(session, folder_id, user)
    rows = list(session.exec(select(Recipe).join(FolderRecipe, FolderRecipe.recipe_id == Recipe.id)
                             .where(FolderRecipe.folder_id == folder.id, Recipe.hidden == False).order_by(col(FolderRecipe.added_at).desc())))  # noqa: E712
    return FolderDetail(id=folder.id, name=folder.name, recipes=summaries(session, rows, user))


@router.put("/folders/{folder_id}/recipes/{recipe_id}")
def add_to_folder(folder_id: int, recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    owned_folder(session, folder_id, user)
    owned_recipe(session, recipe_id, user)
    if not session.exec(select(FolderRecipe).where(FolderRecipe.folder_id == folder_id, FolderRecipe.recipe_id == recipe_id)).first():
        session.add(FolderRecipe(folder_id=folder_id, recipe_id=recipe_id))
        session.commit()
    return {"ok": True}


@router.delete("/folders/{folder_id}/recipes/{recipe_id}")
def remove_from_folder(folder_id: int, recipe_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    owned_folder(session, folder_id, user)
    session.exec(delete(FolderRecipe).where(FolderRecipe.folder_id == folder_id, FolderRecipe.recipe_id == recipe_id))
    session.commit()
    return {"ok": True}


# --- variations

@router.post("/recipes/{recipe_id}/variation")
def make_variation(recipe_id: int, body: RecipeIn | None = None, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Copy a recipe (ingredients with their foods and weights, steps, photo) as your own.

    With body (the editor's form, as saved), the copy is written as that: the apps open the editor
    on the original and only make the copy when you save, so backing out leaves nothing behind.
    Ingredients you didn't change keep the original's foods and hand-fixed weights.
    """
    src = owned_recipe(session, recipe_id, user)
    copy = Recipe(**src.model_dump(exclude={"id", "owner_id", "source", "parent_id", "created_at", "updated_at", "title", "slug"}),
                  owner_id=user.id, source="manual", parent_id=src.id, title=f"{src.title} (my version)", slug=f"{src.slug}-mine")
    session.add(copy)
    session.flush()
    for i in session.exec(select(Ingredient).where(Ingredient.recipe_id == src.id)):
        session.add(Ingredient(**i.model_dump(exclude={"id", "recipe_id"}), recipe_id=copy.id))
    for st in session.exec(select(Step).where(Step.recipe_id == src.id)):
        session.add(Step(**st.model_dump(exclude={"id", "recipe_id"}), recipe_id=copy.id))
    session.flush()
    if body is not None:
        if not body.title.strip():
            raise HTTPException(400, "a recipe needs a title")
        save_recipe(session, copy, body)
    session.commit()
    return {"id": copy.id}

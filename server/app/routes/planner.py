from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, select

from ..db import get_session
from ..deps import current_user
from ..models import PlanEntry, Recipe, User
from .recipes import nutrition_for, owned_recipe

router = APIRouter()


class EntryOut(SQLModel):
    id: int
    day: date | None
    position: int
    recipe_id: int | None
    title: str
    image_url: str | None
    servings: float
    kcal_per_serving: float | None
    kcal: float | None  # for all planned servings


class PlanOut(SQLModel):
    days: dict[date, list[EntryOut]]
    queue: list[EntryOut]


class EntryIn(SQLModel):
    day: date | None = None
    recipe_id: int | None = None
    title: str = ""
    servings: float = 1
    position: int | None = None  # default: end of the day


class EntryPatch(SQLModel):
    day: date | None = None
    position: int | None = None
    servings: float | None = None
    title: str | None = None


def entries_out(session: Session, entries: list[PlanEntry]) -> list[EntryOut]:
    recipes = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_({e.recipe_id for e in entries if e.recipe_id})))}
    nutrition = nutrition_for(session, list(recipes.values()))
    out = []
    for e in entries:
        r = recipes.get(e.recipe_id)
        per = ((nutrition[r.id]["per_serving"] or {}).get("kcal")) if r else None
        out.append(EntryOut(id=e.id, day=e.day, position=e.position, recipe_id=e.recipe_id,
                            title=r.title if r else e.title, image_url=r.image_url if r else None,
                            servings=e.servings, kcal_per_serving=per,
                            kcal=round(per * e.servings, 1) if per is not None else None))
    return out


def siblings(session: Session, user: User, day: date | None) -> list[PlanEntry]:
    day_matches = col(PlanEntry.day).is_(None) if day is None else PlanEntry.day == day
    stmt = select(PlanEntry).where(PlanEntry.owner_id == user.id, day_matches)
    return list(session.exec(stmt.order_by(PlanEntry.position, PlanEntry.id)))


def place(session: Session, user: User, entry: PlanEntry, day: date | None, position: int | None) -> None:
    """Put entry into day's list at position and renumber that list (and the one it left)."""
    old_day = entry.day
    entry.day = day
    target = [e for e in siblings(session, user, day) if e.id != entry.id]
    target.insert(len(target) if position is None else max(0, min(position, len(target))), entry)
    for i, e in enumerate(target):
        e.position = i
        session.add(e)
    if old_day != day and entry.id is not None:
        for i, e in enumerate(x for x in siblings(session, user, old_day) if x.id != entry.id):
            e.position = i
            session.add(e)


def owned_entry(session: Session, entry_id: int, user: User) -> PlanEntry:
    entry = session.get(PlanEntry, entry_id)
    if entry is None or entry.owner_id != user.id:
        raise HTTPException(404, "plan entry not found")
    return entry


@router.get("/plan", response_model=PlanOut)
def get_plan(start: date, days: int = 7, session: Session = Depends(get_session), user: User = Depends(current_user)):
    end = start + timedelta(days=days - 1)
    dated = session.exec(select(PlanEntry).where(PlanEntry.owner_id == user.id, PlanEntry.day >= start, PlanEntry.day <= end)
                         .order_by(PlanEntry.day, PlanEntry.position)).all()
    out = entries_out(session, list(dated) + siblings(session, user, None))
    by_day: dict[date, list[EntryOut]] = {start + timedelta(days=i): [] for i in range(days)}
    for e in out:
        if e.day is not None:
            by_day[e.day].append(e)
    return PlanOut(days=by_day, queue=[e for e in out if e.day is None])


@router.post("/plan", response_model=EntryOut)
def add_entry(body: EntryIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    if body.recipe_id is not None:
        owned_recipe(session, body.recipe_id, user)
    elif not body.title.strip():
        raise HTTPException(400, "need a recipe or a title")
    entry = PlanEntry(owner_id=user.id, recipe_id=body.recipe_id, title=body.title.strip(), servings=body.servings)
    session.add(entry)
    session.flush()
    place(session, user, entry, body.day, body.position)
    session.commit()
    return entries_out(session, [entry])[0]


@router.patch("/plan/{entry_id}", response_model=EntryOut)
def update_entry(entry_id: int, body: EntryPatch, session: Session = Depends(get_session), user: User = Depends(current_user)):
    entry = owned_entry(session, entry_id, user)
    fields = body.model_dump(exclude_unset=True)
    if "servings" in fields and fields["servings"]:
        entry.servings = fields["servings"]
    if "title" in fields and fields["title"] is not None:
        entry.title = fields["title"]
    if "day" in fields or "position" in fields:
        place(session, user, entry, fields.get("day", entry.day), fields.get("position"))
    session.add(entry)
    session.commit()
    return entries_out(session, [entry])[0]


@router.delete("/plan/{entry_id}")
def delete_entry(entry_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    entry = owned_entry(session, entry_id, user)
    day = entry.day
    session.delete(entry)
    session.flush()
    for i, e in enumerate(siblings(session, user, day)):
        e.position = i
        session.add(e)
    session.commit()
    return {"ok": True}

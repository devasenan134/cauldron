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
    servings: float  # eaten at this meal
    kcal_per_serving: float | None
    kcal: float | None  # for the servings eaten here
    cook_portions: float | None  # set when this meal cooks a batch
    portions_left: float | None  # of that batch, after planned leftovers
    discarded: float
    leftover_of: int | None  # the batch this leftover meal eats from


class PlanOut(SQLModel):
    days: dict[date, list[EntryOut]]
    queue: list[EntryOut]


class EntryIn(SQLModel):
    day: date | None = None
    recipe_id: int | None = None
    leftover_of: int | None = None
    title: str = ""
    servings: float = 1
    cook_portions: float | None = None
    position: int | None = None  # default: end of the day


class EntryPatch(SQLModel):
    day: date | None = None
    position: int | None = None
    servings: float | None = None
    title: str | None = None
    cook_portions: float | None = None  # null turns a batch back into a plain meal
    discarded: float | None = None


def entries_out(session: Session, entries: list[PlanEntry]) -> list[EntryOut]:
    # Leftovers show and count as the batch they come from.
    sources = {e.id: e for e in session.exec(select(PlanEntry).where(
        col(PlanEntry.id).in_({e.leftover_of for e in entries if e.leftover_of})))}
    sources.update({e.id: e for e in entries})
    recipe_of = {e.id: (sources[e.leftover_of].recipe_id if e.leftover_of in sources else e.recipe_id) for e in entries}
    recipes = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_({r for r in recipe_of.values() if r})))}
    nutrition = nutrition_for(session, list(recipes.values()))
    left = portions_left(session, [e for e in entries if e.cook_portions is not None])
    out = []
    for e in entries:
        r = recipes.get(recipe_of[e.id])
        source = sources.get(e.leftover_of) if e.leftover_of else None
        per = ((nutrition[r.id]["per_serving"] or {}).get("kcal")) if r else None
        out.append(EntryOut(id=e.id, day=e.day, position=e.position, recipe_id=r.id if r else None,
                            title=r.title if r else (source.title if source else e.title),
                            image_url=r.image_url if r else None,
                            servings=e.servings, kcal_per_serving=per,
                            kcal=round(per * e.servings, 1) if per is not None else None,
                            cook_portions=e.cook_portions, portions_left=left.get(e.id), discarded=e.discarded,
                            leftover_of=e.leftover_of))
    return out


def portions_left(session: Session, batches: list[PlanEntry]) -> dict[int, float]:
    """Portions of each batch not yet eaten, planned as leftovers, or thrown away."""
    ids = [b.id for b in batches]
    taken: dict[int, float] = {}
    for lo in session.exec(select(PlanEntry).where(col(PlanEntry.leftover_of).in_(ids))):
        taken[lo.leftover_of] = taken.get(lo.leftover_of, 0) + lo.servings
    return {b.id: round(b.cook_portions - b.servings - taken.get(b.id, 0) - b.discarded, 2) for b in batches}


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
    if body.leftover_of is not None:
        source = owned_entry(session, body.leftover_of, user)
        if source.cook_portions is None:
            raise HTTPException(400, "that meal isn't a batch")
        body.recipe_id = None
        body.cook_portions = None
    elif body.recipe_id is not None:
        owned_recipe(session, body.recipe_id, user)
    elif not body.title.strip():
        raise HTTPException(400, "need a recipe, a batch or a title")
    entry = PlanEntry(owner_id=user.id, recipe_id=body.recipe_id, leftover_of=body.leftover_of,
                      title=body.title.strip(), servings=body.servings, cook_portions=body.cook_portions)
    check_batch(entry)
    session.add(entry)
    session.flush()
    place(session, user, entry, body.day, body.position)
    session.commit()
    return entries_out(session, [entry])[0]


def check_batch(entry: PlanEntry) -> None:
    if entry.cook_portions is not None and entry.cook_portions < entry.servings:
        raise HTTPException(400, "a batch must cook at least the servings eaten at that meal")


@router.patch("/plan/{entry_id}", response_model=EntryOut)
def update_entry(entry_id: int, body: EntryPatch, session: Session = Depends(get_session), user: User = Depends(current_user)):
    entry = owned_entry(session, entry_id, user)
    fields = body.model_dump(exclude_unset=True)
    if "servings" in fields and fields["servings"]:
        entry.servings = fields["servings"]
    if "title" in fields and fields["title"] is not None:
        entry.title = fields["title"]
    if "cook_portions" in fields:
        if entry.recipe_id is None:
            raise HTTPException(400, "only a recipe can be batch cooked")
        entry.cook_portions = fields["cook_portions"]
        if entry.cook_portions is None:
            entry.discarded = 0
            # Its leftovers go with it.
            for lo in session.exec(select(PlanEntry).where(PlanEntry.leftover_of == entry.id)):
                session.delete(lo)
    if fields.get("discarded") is not None:
        entry.discarded = max(0, fields["discarded"])
    check_batch(entry)
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


@router.get("/batches", response_model=list[EntryOut])
def batches(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Batches that still have portions left, oldest first; unscheduled ones last."""
    cooks = list(session.exec(select(PlanEntry).where(PlanEntry.owner_id == user.id,
                                                      col(PlanEntry.cook_portions).is_not(None))))
    left = portions_left(session, cooks)
    out = entries_out(session, [c for c in cooks if left[c.id] > 0])
    return sorted(out, key=lambda e: (e.day is None, e.day or date.max, e.id))

from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, select

from ..db import get_session
from ..deps import current_user
from ..models import PlanEntry, Recipe, User
from ..prep import Ledger, ledger
from .recipes import owned_recipe

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
    is_prep: bool = False  # cooks a prepped ingredient (made_grams of it); nothing is eaten here
    made_grams: float | None = None
    grams_left: float | None = None  # of that prep, after what's planned to use it
    # Preps this meal uses that nothing planned covers (the grocery list buys their ingredients).
    short: list[dict] = []  # [{"prep_id", "title", "grams"}]


class PrepUse(SQLModel):
    entry_id: int
    day: date | None
    title: str
    grams: float


class PrepStock(SQLModel):
    """A prep you made (or will make): what's left of it, and what's planned to use it."""
    entry_id: int
    recipe_id: int
    title: str
    image_url: str | None
    day: date | None
    made_grams: float
    discarded: float
    grams_now: float  # in the fridge now: made, less what meals before today used and what was thrown out
    grams_left: float  # spare once the planned meals have taken theirs
    kcal_per_100g: float | None
    uses: list[PrepUse]


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
    made_grams: float | None = None  # a prep: how much you make (default: the whole recipe)
    position: int | None = None  # default: end of the day


class EntryPatch(SQLModel):
    day: date | None = None
    position: int | None = None
    servings: float | None = None
    title: str | None = None
    cook_portions: float | None = None  # null turns a batch back into a plain meal
    discarded: float | None = None
    made_grams: float | None = None


def entries_out(session: Session, user: User, entries: list[PlanEntry], book: Ledger | None = None) -> list[EntryOut]:
    # Leftovers show and count as the batch they come from.
    sources = {e.id: e for e in session.exec(select(PlanEntry).where(
        col(PlanEntry.id).in_({e.leftover_of for e in entries if e.leftover_of})))}
    sources.update({e.id: e for e in entries})
    recipe_of = {e.id: (sources[e.leftover_of].recipe_id if e.leftover_of in sources else e.recipe_id) for e in entries}
    book = book or ledger(session, user)
    kitchen = book.kitchen
    missing = {r for r in recipe_of.values() if r and r not in kitchen.recipes}
    if missing:  # leftovers of batches from elsewhere; normally all are in the ledger already
        kitchen.recipes.update({r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_(missing)))})
    left = portions_left(session, [e for e in entries if e.cook_portions is not None])
    out = []
    for e in entries:
        r = kitchen.recipes.get(recipe_of[e.id])
        source = sources.get(e.leftover_of) if e.leftover_of else None
        prep = bool(r and r.is_prep and not e.leftover_of)
        per = None if prep or not r else ((kitchen.nutrition(r.id)["per_serving"] or {}).get("kcal"))
        out.append(EntryOut(id=e.id, day=e.day, position=e.position, recipe_id=r.id if r else None,
                            title=r.title if r else (source.title if source else e.title),
                            image_url=r.image_url if r else None,
                            servings=e.servings, kcal_per_serving=per,
                            kcal=round(per * e.servings, 1) if per is not None else None,
                            cook_portions=e.cook_portions, portions_left=left.get(e.id), discarded=e.discarded,
                            leftover_of=e.leftover_of, is_prep=prep,
                            made_grams=book.made.get(e.id) if prep else None,
                            grams_left=book.left(e) if prep else None,
                            short=[{"prep_id": p, "title": kitchen.recipes[p].title if p in kitchen.recipes else "", "grams": g}
                                   for (eid, p), g in book.short.items() if eid == e.id]))
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
    out = entries_out(session, user, list(dated) + siblings(session, user, None))
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
        recipe = owned_recipe(session, body.recipe_id, user)
        if recipe.is_prep:
            # Making a prep: nothing is eaten at this entry; what's made goes to the fridge.
            body.servings, body.cook_portions = 0, None
    elif not body.title.strip():
        raise HTTPException(400, "need a recipe, a batch or a title")
    entry = PlanEntry(owner_id=user.id, recipe_id=body.recipe_id, leftover_of=body.leftover_of,
                      title=body.title.strip(), servings=body.servings, cook_portions=body.cook_portions,
                      made_grams=body.made_grams if body.made_grams and body.made_grams > 0 else None)
    check_batch(entry)
    session.add(entry)
    session.flush()
    place(session, user, entry, body.day, body.position)
    session.commit()
    return entries_out(session, user, [entry])[0]


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
        if session.get(Recipe, entry.recipe_id).is_prep:
            raise HTTPException(400, "a prep is made by weight (made_grams), not in portions")
        entry.cook_portions = fields["cook_portions"]
        if entry.cook_portions is None:
            entry.discarded = 0
            # Its leftovers go with it.
            for lo in session.exec(select(PlanEntry).where(PlanEntry.leftover_of == entry.id)):
                session.delete(lo)
    if fields.get("discarded") is not None:
        entry.discarded = max(0, fields["discarded"])
    if "made_grams" in fields:
        entry.made_grams = fields["made_grams"] if fields["made_grams"] and fields["made_grams"] > 0 else None
    check_batch(entry)
    if "day" in fields or "position" in fields:
        place(session, user, entry, fields.get("day", entry.day), fields.get("position"))
    session.add(entry)
    session.commit()
    return entries_out(session, user, [entry])[0]


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
    out = entries_out(session, user, [c for c in cooks if left[c.id] > 0])
    return sorted(out, key=lambda e: (e.day is None, e.day or date.max, e.id))


@router.get("/prep-stock", response_model=list[PrepStock])
def prep_stock(all: bool = False, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Preps you've made (still in the fridge) or are going to make, with the meals that use them; oldest first.

    all: also the ones that are used up.
    """
    book = ledger(session, user)
    kitchen = book.kitchen
    consumers = {e.id: e for e in session.exec(select(PlanEntry).where(
        col(PlanEntry.id).in_({c for uses in book.used.values() for c, _ in uses})))}
    out = []
    today = date.today()
    for s in book.sessions:
        left = book.left(s)
        past = sum(g for c, g in book.used.get(s.id, []) if c in consumers and consumers[c].day and consumers[c].day < today)
        now = round(book.made[s.id] - s.discarded - past, 1)
        made_yet = s.day is not None and s.day <= today
        if not all and made_yet and now <= 0.5:
            continue
        recipe = kitchen.recipes[s.recipe_id]
        per100 = kitchen.per100(recipe.id)
        uses = [PrepUse(entry_id=c, day=consumers[c].day, grams=g,
                        title=kitchen.recipes[consumers[c].recipe_id].title if consumers[c].recipe_id in kitchen.recipes else "")
                for c, g in book.used.get(s.id, []) if c in consumers]
        out.append(PrepStock(entry_id=s.id, recipe_id=recipe.id, title=recipe.title, image_url=recipe.image_url, day=s.day,
                             made_grams=round(book.made[s.id], 1), discarded=s.discarded, grams_now=max(0, now), grams_left=max(0, left),
                             kcal_per_100g=round(per100.kcal, 1) if per100 else None, uses=uses))
    return out

import math
from datetime import date

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, delete, select

from ..db import get_session
from ..deps import current_user
from ..models import GroceryItem, Ingredient, PlanEntry, Recipe, User
from ..nutrition import count_from_label

router = APIRouter()

# Store walk order; unknown aisles go last.
AISLES = ["Produce", "Meat & Seafood", "Dairy & Eggs", "Bakery", "Deli", "Frozen", "Pantry", "Spices",
          "Condiments", "International", "Beverages", "Other"]
SKIP = {"water", "ice water", "ice cubes", "pasta water"}
NO_AMOUNT = {"", "to taste", "as needed", "for garnish", "for serving"}


class GenerateIn(SQLModel):
    start: date
    end: date
    include_queue: bool = False


class ItemIn(SQLModel):
    name: str
    amount: str = ""
    aisle: str = "Other"


class ItemPatch(SQLModel):
    name: str | None = None
    amount: str | None = None
    aisle: str | None = None
    checked: bool | None = None


def fmt_num(n: float) -> str:
    return f"{n:.1f}".rstrip("0").rstrip(".")


class Tally:
    """Adds up one grocery line across recipes: counts per unit, grams, and loose labels."""

    def __init__(self, name: str, aisle: str | None):
        self.name, self.aisle = name, aisle
        self.counts: dict[str, float] = {}
        self.grams = 0.0
        self.labels: list[str] = []
        self.sources: list[str] = []

    def add(self, ing: Ingredient, factor: float, source: str) -> None:
        self.aisle = self.aisle or ing.aisle
        if source not in self.sources:
            self.sources.append(source)
        unit = (ing.unit or "").lower()
        if ing.grams_source == "portion" and (n := count_from_label(ing.label or "", ing.amount)):
            key = "" if unit in ("", "ingredient") else unit.rstrip("s")
            self.counts[key] = self.counts.get(key, 0) + n * factor
        elif ing.grams is not None and ing.grams_source != "estimate":
            self.grams += ing.grams * factor
        elif (label := (ing.label or "").strip().lower()) not in NO_AMOUNT and label not in self.labels:
            self.labels.append(label)

    def amount(self) -> str:
        parts = []
        for unit, n in self.counts.items():
            n = math.ceil(n - 1e-9)  # you buy whole onions, lemons, buns
            plural = unit and n > 1 and not unit.endswith("s")
            parts.append(f"{fmt_num(n)} {unit}{'s' if plural else ''}".strip())
        if self.grams:
            parts.append(f"{fmt_num(self.grams / 1000)} kg" if self.grams >= 1000 else f"{round(self.grams)} g")
        text = " + ".join(parts)
        if self.labels:
            text = ", ".join(filter(None, [text, *self.labels]))
        return text


def sorted_items(items: list[GroceryItem]) -> list[GroceryItem]:
    order = {a: i for i, a in enumerate(AISLES)}
    return sorted(items, key=lambda i: (i.checked, order.get(i.aisle, len(AISLES)), i.name.lower()))


@router.get("/grocery", response_model=list[GroceryItem])
def list_items(session: Session = Depends(get_session), user: User = Depends(current_user)):
    return sorted_items(list(session.exec(select(GroceryItem).where(GroceryItem.owner_id == user.id))))


@router.post("/grocery/generate", response_model=list[GroceryItem])
def generate(body: GenerateIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Rebuild the list from planned meals. Hand-added items stay; ticks carry over by name."""
    in_range = (PlanEntry.day >= body.start) & (PlanEntry.day <= body.end)
    where = (in_range | col(PlanEntry.day).is_(None)) if body.include_queue else in_range
    entries = session.exec(select(PlanEntry).where(PlanEntry.owner_id == user.id, where,
                                                   col(PlanEntry.recipe_id).is_not(None))).all()
    recipes = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_({e.recipe_id for e in entries})))}
    ings = session.exec(select(Ingredient).where(col(Ingredient.recipe_id).in_(recipes)).order_by(Ingredient.position)).all()
    by_recipe: dict[int, list[Ingredient]] = {}
    for i in ings:
        by_recipe.setdefault(i.recipe_id, []).append(i)

    tallies: dict[str, Tally] = {}
    for e in entries:
        recipe = recipes[e.recipe_id]
        portions = e.cook_portions if e.cook_portions is not None else e.servings
        factor = portions / recipe.servings if recipe.servings else 1
        for ing in by_recipe.get(recipe.id, []):
            key = ing.name.strip().lower()
            if key in SKIP:
                continue
            tally = tallies.setdefault(key, Tally(ing.name.strip(), ing.aisle))
            tally.add(ing, factor, recipe.title)

    old = session.exec(select(GroceryItem).where(GroceryItem.owner_id == user.id, GroceryItem.manual == False)).all()  # noqa: E712
    was_checked = {i.name.lower() for i in old if i.checked}
    session.exec(delete(GroceryItem).where(GroceryItem.owner_id == user.id, GroceryItem.manual == False))  # noqa: E712
    for key, t in tallies.items():
        session.add(GroceryItem(owner_id=user.id, name=t.name, amount=t.amount(), aisle=t.aisle or "Other",
                                sources=t.sources, checked=key in was_checked))
    session.commit()
    return list_items(session, user)


@router.post("/grocery", response_model=GroceryItem)
def add_item(body: ItemIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    if not body.name.strip():
        raise HTTPException(400, "name required")
    item = GroceryItem(owner_id=user.id, name=body.name.strip(), amount=body.amount, aisle=body.aisle, manual=True)
    session.add(item)
    session.commit()
    session.refresh(item)
    return item


def owned_item(session: Session, item_id: int, user: User) -> GroceryItem:
    item = session.get(GroceryItem, item_id)
    if item is None or item.owner_id != user.id:
        raise HTTPException(404, "item not found")
    return item


@router.patch("/grocery/{item_id}", response_model=GroceryItem)
def update_item(item_id: int, body: ItemPatch, session: Session = Depends(get_session), user: User = Depends(current_user)):
    item = owned_item(session, item_id, user)
    for k, v in body.model_dump(exclude_unset=True).items():
        if v is not None:
            setattr(item, k, v)
    session.add(item)
    session.commit()
    session.refresh(item)
    return item


@router.delete("/grocery/{item_id}")
def delete_item(item_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    session.delete(owned_item(session, item_id, user))
    session.commit()
    return {"ok": True}


@router.delete("/grocery")
def clear(checked_only: bool = True, session: Session = Depends(get_session), user: User = Depends(current_user)):
    stmt = delete(GroceryItem).where(GroceryItem.owner_id == user.id)
    if checked_only:
        stmt = stmt.where(GroceryItem.checked == True)  # noqa: E712
    session.exec(stmt)
    session.commit()
    return {"ok": True}

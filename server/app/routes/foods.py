"""Ingredients and their macros: search foods, keep your own versions, add foods of your own.

Your version of a shared food (your brand of curd) replaces it in every recipe you see; other
people keep the shared one. Foods you add yourself (off a label) can be picked for ingredients.
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, SQLModel, col, func, select

from ..db import get_session
from ..deps import current_user
from ..models import Food, Ingredient, Recipe, User
from ..prep import effective_foods
from ..recipe_edit import aisle_for, relink_recipe
from .recipes import can_edit, visible

router = APIRouter()

NUTRIENTS = ("kcal", "protein", "fat", "carbs", "fiber", "sugar", "sodium_mg")


class FoodOut(SQLModel):
    id: int  # the id to link ingredients to (the shared food's, when you have your own version)
    name: str
    source: str
    brand: str = ""
    notes: str = ""
    kcal: float
    protein: float
    fat: float
    carbs: float
    fiber: float | None = None
    sugar: float | None = None
    sodium_mg: float | None = None
    edited: bool = False  # your own version of a shared food
    own: bool = False  # a food you added


class FoodRow(FoodOut):
    recipes: int = 0  # recipes you can see that use it
    names: list[str] = []  # what those recipes call it


class FoodDetail(FoodRow):
    default: dict | None = None  # the shared values, when you have your own version
    portions: list[dict] = []
    category: str | None = None
    used_in: list[dict] = []  # [{"id", "title"}]


class FoodIn(SQLModel):
    name: str
    brand: str = ""
    notes: str = ""
    kcal: float = 0
    protein: float = 0
    fat: float = 0
    carbs: float = 0
    fiber: float | None = None
    sugar: float | None = None
    sodium_mg: float | None = None


class AssignIn(SQLModel):
    name: str  # ingredient name, as recipes write it


def food_out(food: Food, cls=FoodOut, **extra):
    """food as the user sees it: their version's values under the shared food's id."""
    return cls(**food.model_dump(exclude={"id", "portions", "category"}), id=food.base_id or food.id,
               edited=food.base_id is not None, own=food.owner_id is not None and food.base_id is None, **extra)


def shared_or_mine(user: User):
    return (col(Food.owner_id).is_(None)) | ((Food.owner_id == user.id) & col(Food.base_id).is_(None))


def usable_food(session: Session, food_id: int, user: User) -> Food:
    food = session.get(Food, food_id)
    if food is None or food.owner_id not in (None, user.id):
        raise HTTPException(404, "food not found")
    return session.get(Food, food.base_id) if food.base_id else food


@router.get("/foods", response_model=list[FoodOut])
def search_foods(q: str, limit: int = 25, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Foods whose name contains every word of q (yours first, then shortest names)."""
    stmt = select(Food).where(shared_or_mine(user))
    for word in q.split():
        stmt = stmt.where(col(Food.name).ilike(f"%{word}%"))
    foods = list(session.exec(stmt.limit(2000)))
    rank = {"user": -1, "custom": 0, "usda_sr_legacy": 1, "usda_foundation": 2}
    foods.sort(key=lambda f: (f.kcal == 0 and "water" not in f.name.lower() and "salt" not in f.name.lower(),
                              rank.get(f.source, 3), len(f.name)))
    foods = foods[:limit]
    mine = effective_foods(session, user, {f.id for f in foods})
    return [food_out(mine.get(f.id, f)) for f in foods]


def usage(session: Session, user: User) -> dict[int, tuple[set[int], dict[str, int]]]:
    """Food id -> (recipes using it, names they call it with counts), over recipes you can see."""
    rows = session.exec(select(Ingredient.food_id, Ingredient.recipe_id, Ingredient.name)
                        .join(Recipe, Recipe.id == Ingredient.recipe_id)
                        .where(visible(user), col(Ingredient.food_id).is_not(None)))
    out: dict[int, tuple[set[int], dict[str, int]]] = {}
    for food_id, recipe_id, name in rows:
        recipes, names = out.setdefault(food_id, (set(), {}))
        recipes.add(recipe_id)
        names[name.lower()] = names.get(name.lower(), 0) + 1
    return out


def row(food: Food, used: tuple[set[int], dict[str, int]] | None, cls=FoodRow, **extra):
    recipes, names = used or (set(), {})
    return food_out(food, cls, recipes=len(recipes), names=sorted(names, key=lambda n: -names[n])[:6], **extra)


@router.get("/foods/library", response_model=list[FoodRow])
def library(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Every food your recipes use, plus the ones you added; most used first."""
    used = usage(session, user)
    foods = effective_foods(session, user, set(used))
    for f in session.exec(select(Food).where(Food.owner_id == user.id, col(Food.base_id).is_(None))):
        foods.setdefault(f.id, f)
    rows = [row(f, used.get(fid)) for fid, f in foods.items()]
    return sorted(rows, key=lambda r: (-r.recipes, r.name.lower()))


class Unlinked(SQLModel):
    name: str
    count: int  # ingredients with this name
    recipes: list[dict]  # [{"id", "title"}], a few


@router.get("/foods/unlinked", response_model=list[Unlinked])
def unlinked(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Ingredients in recipes you can edit that have no food, so they add no calories."""
    rows = session.exec(select(Ingredient.name, Recipe).join(Recipe, Recipe.id == Ingredient.recipe_id)
                        .where(visible(user), col(Ingredient.food_id).is_(None), col(Ingredient.prep_id).is_(None)))
    out: dict[str, Unlinked] = {}
    for name, recipe in rows:
        key = name.strip().lower()
        if not can_edit(recipe, user) or key in ("water", "ice", "ice water", "pasta water", "boiling water"):
            continue
        u = out.setdefault(key, Unlinked(name=name.strip(), count=0, recipes=[]))
        u.count += 1
        if len(u.recipes) < 5 and all(r["id"] != recipe.id for r in u.recipes):
            u.recipes.append({"id": recipe.id, "title": recipe.title})
    return sorted(out.values(), key=lambda u: (-u.count, u.name.lower()))


@router.get("/foods/{food_id}", response_model=FoodDetail)
def get_food(food_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    base = usable_food(session, food_id, user)
    mine = effective_foods(session, user, {base.id})[base.id]
    recipes = session.exec(select(Recipe).where(visible(user), col(Recipe.id).in_(
        select(Ingredient.recipe_id).where(Ingredient.food_id == base.id))).order_by(Recipe.title)).all()
    names: dict[str, int] = {}
    for name in session.exec(select(Ingredient.name).join(Recipe, Recipe.id == Ingredient.recipe_id)
                             .where(visible(user), Ingredient.food_id == base.id)):
        names[name.lower()] = names.get(name.lower(), 0) + 1
    default = {k: getattr(base, k) for k in ("name", *NUTRIENTS)} if mine is not base else None
    return row(mine, ({r.id for r in recipes}, names), FoodDetail, default=default, portions=base.portions,
               category=base.category, used_in=[{"id": r.id, "title": r.title} for r in recipes])


@router.put("/foods/{food_id}", response_model=FoodDetail)
def save_food(food_id: int, body: FoodIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Change a food for yourself: a food you added is edited; a shared one gets your own version."""
    if not body.name.strip():
        raise HTTPException(400, "a food needs a name")
    base = usable_food(session, food_id, user)
    if base.owner_id == user.id:
        target = base
    else:
        target = session.exec(select(Food).where(Food.owner_id == user.id, Food.base_id == base.id)).first()
        if target is None:
            target = Food(name=base.name, source="user", owner_id=user.id, base_id=base.id,
                          category=base.category, portions=base.portions)
    for k, v in body.model_dump().items():
        setattr(target, k, v.strip() if isinstance(v, str) else v)
    session.add(target)
    session.commit()
    return get_food(base.id, session, user)


@router.delete("/foods/{food_id}")
def reset_food(food_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Go back to the shared values (your version goes), or delete a food you added."""
    base = usable_food(session, food_id, user)
    if base.owner_id == user.id:
        for ing in session.exec(select(Ingredient).where(Ingredient.food_id == base.id)):
            ing.food_id = None
            session.add(ing)
        session.flush()
        session.delete(base)
    else:
        for mine in session.exec(select(Food).where(Food.owner_id == user.id, Food.base_id == base.id)):
            session.delete(mine)
    session.commit()
    return {"ok": True}


@router.post("/foods", response_model=FoodDetail)
def add_food(body: FoodIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """A food of your own, e.g. a product off its label (values per 100 g)."""
    if not body.name.strip():
        raise HTTPException(400, "a food needs a name")
    food = Food(**{k: v.strip() if isinstance(v, str) else v for k, v in body.model_dump().items()},
                source="user", owner_id=user.id)
    session.add(food)
    session.commit()
    return get_food(food.id, session, user)


@router.post("/foods/{food_id}/assign")
def assign_food(food_id: int, body: AssignIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Use this food for every ingredient called body.name that has none (in recipes you can edit)."""
    food = usable_food(session, food_id, user)
    rows = session.exec(select(Ingredient, Recipe).join(Recipe, Recipe.id == Ingredient.recipe_id)
                        .where(visible(user), col(Ingredient.food_id).is_(None), col(Ingredient.prep_id).is_(None),
                               func.lower(func.trim(Ingredient.name)) == body.name.strip().lower()))
    changed: dict[int, tuple[Recipe, set[int]]] = {}
    for ing, recipe in rows:
        if not can_edit(recipe, user):
            continue
        ing.food_id = food.id
        ing.aisle = ing.aisle or aisle_for(food)
        session.add(ing)
        changed.setdefault(recipe.id, (recipe, set()))[1].add(ing.id)
    session.flush()
    for recipe, ids in changed.values():
        relink_recipe(session, recipe, ids)
    session.commit()
    return {"ok": True, "ingredients": sum(len(ids) for _, ids in changed.values()), "recipes": len(changed)}

"""Prepped ingredients, and nutrition as each user sees it.

A prep is a recipe other recipes use by weight (cooked rice, pickled onions, a sauce). Its
nutrition per 100 g is its total over what it weighs when done. Preps can use other preps.

Cooking a prep is a plan entry with made_grams. Everything on the plan that uses a prep (meals,
batches, other preps) draws on the prep sessions on or before its day, oldest first; what isn't
drawn is in the fridge, and what can't be covered is bought as the prep's raw ingredients.

Foods: a user's version of a shared food (base_id) replaces it in every recipe they see.
"""
from dataclasses import dataclass, field
from datetime import date
from types import SimpleNamespace

from sqlmodel import Session, col, select

from .models import Food, Ingredient, PlanEntry, Recipe, User
from .nutrition import MACROS, prep_yield, recipe_nutrition

MAX_DEPTH = 5  # preps inside preps


def effective_foods(session: Session, user: User | None, ids) -> dict[int, Food]:
    """Foods by id, with the user's own versions standing in for the shared ones."""
    ids = {i for i in ids if i}
    foods = {f.id: f for f in session.exec(select(Food).where(col(Food.id).in_(ids)))}
    if user is not None and ids:
        for mine in session.exec(select(Food).where(Food.owner_id == user.id, col(Food.base_id).in_(ids))):
            foods[mine.base_id] = mine
    return foods


def ingredients_of(session: Session, recipe_ids) -> dict[int, list[Ingredient]]:
    out: dict[int, list[Ingredient]] = {}
    for i in session.exec(select(Ingredient).where(col(Ingredient.recipe_id).in_(set(recipe_ids))).order_by(Ingredient.position)):
        out.setdefault(i.recipe_id, []).append(i)
    return out


class Kitchen:
    """Recipes, their ingredients, and the preps they use (loaded once), for nutrition and stock."""

    def __init__(self, session: Session, user: User | None, recipes: list[Recipe]):
        self.session, self.user = session, user
        self.recipes = {r.id: r for r in recipes}
        self.ings = ingredients_of(session, self.recipes)
        # Pull in preps used by these recipes, and preps those use.
        todo = self._prep_ids(self.recipes)
        for _ in range(MAX_DEPTH):
            todo -= set(self.recipes)
            if not todo:
                break
            more = {r.id: r for r in session.exec(select(Recipe).where(col(Recipe.id).in_(todo)))}
            self.recipes.update(more)
            self.ings.update(ingredients_of(session, more))
            todo = self._prep_ids(more)
        self.foods = effective_foods(session, user, {i.food_id for ings in self.ings.values() for i in ings})
        self._per100: dict[int, SimpleNamespace | None] = {}

    def _prep_ids(self, recipes) -> set[int]:
        return {i.prep_id for rid in recipes for i in self.ings.get(rid, []) if i.prep_id}

    def per100(self, prep_id: int, depth: int = 0) -> SimpleNamespace | None:
        """A prep's macros per 100 g (None if unknown or it uses itself)."""
        if prep_id in self._per100:
            return self._per100[prep_id]
        self._per100[prep_id] = None  # guards against a prep that (indirectly) uses itself
        recipe = self.recipes.get(prep_id)
        if recipe is None or depth > MAX_DEPTH:
            return None
        n = self.nutrition(prep_id, depth + 1)
        grams = self.yield_of(prep_id)
        value = SimpleNamespace(**{m: n["total"][m] * 100 / grams for m in MACROS}) if grams else None
        self._per100[prep_id] = value
        return value

    def yield_of(self, recipe_id: int) -> float | None:
        return prep_yield(self.recipes[recipe_id], self.ings.get(recipe_id, [])) if recipe_id in self.recipes else None

    def nutrition(self, recipe_id: int, depth: int = 0) -> dict:
        recipe = self.recipes[recipe_id]
        ings = self.ings.get(recipe_id, [])
        preps = {i.prep_id: self.per100(i.prep_id, depth) for i in ings if i.prep_id}
        out = recipe_nutrition(ings, self.foods, recipe.servings, preps)
        if recipe.is_prep:
            grams = self.yield_of(recipe_id)
            out["yield_grams"] = grams
            out["per_100g"] = {k: round(v * 100 / grams, 1) for k, v in out["total"].items()} if grams else None
        return out


def nutrition_for(session: Session, recipes: list[Recipe], user: User | None = None) -> dict[int, dict]:
    """recipe_nutrition() for many recipes, as this user sees them (their foods, their preps)."""
    kitchen = Kitchen(session, user, recipes)
    return {r.id: kitchen.nutrition(r.id) for r in recipes}


# --- stock: prep sessions and what draws on them

@dataclass
class Ledger:
    made: dict[int, float] = field(default_factory=dict)  # prep session entry -> grams made
    used: dict[int, list[tuple[int, float]]] = field(default_factory=dict)  # session -> [(consumer entry, grams)]
    short: dict[tuple[int, int], float] = field(default_factory=dict)  # (consumer entry, prep id) -> grams not covered
    need: dict[int, list[tuple[int, float, float]]] = field(default_factory=dict)  # consumer -> [(prep id, grams, covered)]
    sessions: list[PlanEntry] = field(default_factory=list)
    kitchen: Kitchen | None = None

    def left(self, entry: PlanEntry) -> float:
        return round(self.made.get(entry.id, 0) - sum(g for _, g in self.used.get(entry.id, [])) - entry.discarded, 1)


def factor(entry: PlanEntry, recipe: Recipe, kitchen: Kitchen) -> float:
    """How many times the recipe this entry makes: a prep by weight, a meal or batch by portions."""
    if recipe.is_prep:
        full = kitchen.yield_of(recipe.id)
        return (made_grams(entry, recipe, kitchen) / full) if full else 1
    portions = entry.cook_portions if entry.cook_portions is not None else entry.servings
    return portions / recipe.servings if recipe.servings else 1


def made_grams(entry: PlanEntry, recipe: Recipe, kitchen: Kitchen) -> float:
    return entry.made_grams if entry.made_grams is not None else (kitchen.yield_of(recipe.id) or 0)


def _when(e: PlanEntry) -> tuple:
    return (e.day is None, e.day or date.max, e.position, e.id)


def ledger(session: Session, user: User) -> Ledger:
    """Match everything on the plan that uses a prep with the prep sessions that made it."""
    entries = list(session.exec(select(PlanEntry).where(PlanEntry.owner_id == user.id, col(PlanEntry.recipe_id).is_not(None))))
    recipes = list(session.exec(select(Recipe).where(col(Recipe.id).in_({e.recipe_id for e in entries}))))
    kitchen = Kitchen(session, user, recipes)
    out = Ledger(kitchen=kitchen)
    stock: dict[int, list[PlanEntry]] = {}
    for e in sorted(entries, key=_when):
        recipe = kitchen.recipes[e.recipe_id]
        if recipe.is_prep:
            out.made[e.id] = made_grams(e, recipe, kitchen)
            out.sessions.append(e)
            stock.setdefault(recipe.id, []).append(e)
    remaining = {s.id: out.made[s.id] - s.discarded for s in out.sessions}
    for e in sorted(entries, key=_when):
        recipe = kitchen.recipes[e.recipe_id]
        f = factor(e, recipe, kitchen)
        needs: dict[int, float] = {}
        for ing in kitchen.ings.get(recipe.id, []):
            if ing.prep_id and ing.grams:
                needs[ing.prep_id] = needs.get(ing.prep_id, 0) + ing.grams * f
        for prep_id, grams in needs.items():
            want = grams
            for s in stock.get(prep_id, []):
                # Only prep made by then (an unscheduled prep only feeds unscheduled meals).
                if want <= 0 or s.id == e.id or (s.day is None and e.day is not None) or (s.day and e.day and s.day > e.day):
                    continue
                take = min(want, max(0.0, remaining[s.id]))
                if take > 0:
                    remaining[s.id] -= take
                    want -= take
                    out.used.setdefault(s.id, []).append((e.id, round(take, 1)))
            if want > 0.5:
                out.short[(e.id, prep_id)] = round(want, 1)
            out.need.setdefault(e.id, []).append((prep_id, round(grams, 1), round(grams - max(want, 0), 1)))
    return out

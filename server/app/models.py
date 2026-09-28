from datetime import date, datetime, timezone

from sqlalchemy import JSON
from sqlmodel import Field, SQLModel


def now() -> datetime:
    return datetime.now(timezone.utc)


class User(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    email: str = Field(unique=True)
    name: str = ""
    kcal_goal: int = 2200  # daily calorie goal, shown on Home (app and website)
    theme: str = "system"  # system | light | dark, for the app and the website
    created_at: datetime = Field(default_factory=now)


class RecipeBase(SQLModel):
    title: str
    slug: str = Field(index=True)
    source: str = "manual"  # manual | cookwell | youtube | instagram
    source_url: str | None = None
    video_url: str | None = None
    image_url: str | None = None
    author: str | None = None
    description: str = ""
    servings: float | None = None  # number used for per-serving maths
    yield_text: str | None = None  # as written, e.g. "3-4 burritos"
    total_minutes: int | None = None
    category: str | None = None
    cuisine: str | None = None
    tags: list[str] = Field(default_factory=list, sa_type=JSON)
    # Nutrition as published by the source, for the whole recipe.
    source_nutrition: dict | None = Field(default=None, sa_type=JSON)
    notes: str = ""


class Recipe(RecipeBase, table=True):
    id: int | None = Field(default=None, primary_key=True)
    owner_id: int = Field(foreign_key="user.id", index=True)
    created_at: datetime = Field(default_factory=now)
    updated_at: datetime = Field(default_factory=now)


class Ingredient(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    recipe_id: int = Field(foreign_key="recipe.id", index=True, ondelete="CASCADE")
    position: int
    group: str | None = None  # component, e.g. "Sauce"
    name: str
    note: str = ""  # "thinly sliced"
    amount: float | None = None
    unit: str | None = None
    label: str = ""  # quantity as written, e.g. "4-6 cloves"
    grams: float | None = None  # weight used for nutrition
    # how grams was found: given | portion | parts | estimate | manual; None = not counted
    grams_source: str | None = None
    food_id: int | None = Field(default=None, foreign_key="food.id")
    aisle: str | None = None


class Step(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    recipe_id: int = Field(foreign_key="recipe.id", index=True, ondelete="CASCADE")
    position: int
    title: str = ""
    text: str


class Food(SQLModel, table=True):
    """A food with nutrients per 100 g: USDA (FoodData Central) or user-made."""
    id: int | None = Field(default=None, primary_key=True)
    fdc_id: int | None = Field(default=None, unique=True)
    name: str = Field(index=True)
    source: str  # usda_foundation | usda_sr_legacy | custom
    category: str | None = None
    kcal: float = 0
    protein: float = 0
    fat: float = 0
    carbs: float = 0
    fiber: float | None = None
    sugar: float | None = None
    sodium_mg: float | None = None
    # USDA household measures: [{"unit": "clove", "grams": 3.0}, ...] (per 1 unit)
    portions: list[dict] = Field(default_factory=list, sa_type=JSON)


class PlanEntry(SQLModel, table=True):
    """A meal on the planner. day=None means it waits in the queue."""
    id: int | None = Field(default=None, primary_key=True)
    owner_id: int = Field(foreign_key="user.id", index=True)
    day: date | None = Field(default=None, index=True)
    position: int = 0  # order within the day (or the queue)
    recipe_id: int | None = Field(default=None, foreign_key="recipe.id", ondelete="CASCADE")
    title: str = ""  # for a custom meal or note with no recipe
    servings: float = 1  # portions eaten at this meal
    # Batch cooking: this meal cooks cook_portions (the grocery list buys for all of them);
    # what isn't eaten here goes in the fridge.
    cook_portions: float | None = None
    # A leftover meal: eats servings portions of the batch cooked by that entry.
    leftover_of: int | None = Field(default=None, foreign_key="planentry.id", ondelete="CASCADE", index=True)
    discarded: float = 0  # batch portions thrown away
    created_at: datetime = Field(default_factory=now)


class GroceryItem(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    owner_id: int = Field(foreign_key="user.id", index=True)
    name: str
    amount: str = ""  # e.g. "5 cloves + 20 g"
    aisle: str = "Other"
    checked: bool = False
    manual: bool = False  # added by hand; kept when the list is regenerated
    sources: list[str] = Field(default_factory=list, sa_type=JSON)  # recipe titles
    created_at: datetime = Field(default_factory=now)


class AuthSession(SQLModel, table=True):
    """A signed-in browser. The cookie holds the token; only its hash is stored."""
    id: int | None = Field(default=None, primary_key=True)
    token_hash: str = Field(unique=True)
    user_id: int = Field(foreign_key="user.id", index=True, ondelete="CASCADE")
    created_at: datetime = Field(default_factory=now)
    expires_at: datetime

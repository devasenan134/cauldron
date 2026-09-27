from datetime import datetime, timezone

from sqlalchemy import JSON
from sqlmodel import Field, SQLModel


def now() -> datetime:
    return datetime.now(timezone.utc)


class User(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    email: str = Field(unique=True)
    name: str = ""
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
    grams: float | None = None  # resolved weight, drives nutrition
    aisle: str | None = None


class Step(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    recipe_id: int = Field(foreign_key="recipe.id", index=True, ondelete="CASCADE")
    position: int
    title: str = ""
    text: str

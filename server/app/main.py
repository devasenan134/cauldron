from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException
from sqlmodel import Session, SQLModel, col, or_, select

from .db import get_session, init_db
from .models import Ingredient, Recipe, RecipeBase, Step
from .users import owner


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(title="Cauldron", lifespan=lifespan)


def current_user(session: Session = Depends(get_session)):
    return owner(session)


class RecipeSummary(SQLModel):
    id: int
    title: str
    image_url: str | None
    source: str
    cuisine: str | None
    category: str | None
    total_minutes: int | None
    servings: float | None
    tags: list[str]


class RecipeDetail(RecipeBase):
    id: int
    ingredients: list[Ingredient]
    steps: list[Step]


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/recipes", response_model=list[RecipeSummary])
def list_recipes(q: str | None = None, cuisine: str | None = None, category: str | None = None,
                 session: Session = Depends(get_session), user=Depends(current_user)):
    stmt = select(Recipe).where(Recipe.owner_id == user.id)
    if q:
        like = f"%{q}%"
        in_ingredients = select(Ingredient.recipe_id).where(col(Ingredient.name).ilike(like))
        stmt = stmt.where(or_(col(Recipe.title).ilike(like), col(Recipe.id).in_(in_ingredients)))
    if cuisine:
        stmt = stmt.where(Recipe.cuisine == cuisine)
    if category:
        stmt = stmt.where(Recipe.category == category)
    return session.exec(stmt.order_by(Recipe.title)).all()


@app.get("/recipes/{recipe_id}", response_model=RecipeDetail)
def get_recipe(recipe_id: int, session: Session = Depends(get_session), user=Depends(current_user)):
    recipe = session.get(Recipe, recipe_id)
    if recipe is None or recipe.owner_id != user.id:
        raise HTTPException(404, "recipe not found")
    ingredients = session.exec(select(Ingredient).where(Ingredient.recipe_id == recipe_id).order_by(Ingredient.position)).all()
    steps = session.exec(select(Step).where(Step.recipe_id == recipe_id).order_by(Step.position)).all()
    return RecipeDetail(**recipe.model_dump(), ingredients=ingredients, steps=steps)

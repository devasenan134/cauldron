"""Your account's data: download all of it, or delete all of it.

Deleting removes every row you own and the photo files only your recipes use. Other people's
rows that point at yours (a variation of your recipe, say) are unhooked first, so they stay.
"""
from datetime import datetime

from sqlmodel import Session, col, delete, select, update

from .importer import UPLOAD_DIR, ImportJob
from .models import (AuthSession, Favorite, Folder, FolderRecipe, Food, GroceryItem, GroceryTemplate, Ingredient,
                     PlanEntry, Recipe, Step, User)
from .routes.images import IMAGE_DIR


def export(session: Session, user: User) -> dict:
    """Everything you've added, as plain JSON."""
    recipes = list(session.exec(select(Recipe).where(Recipe.owner_id == user.id).order_by(Recipe.id)))
    ids = [r.id for r in recipes]
    ings: dict[int, list[dict]] = {}
    for i in session.exec(select(Ingredient).where(col(Ingredient.recipe_id).in_(ids)).order_by(Ingredient.position)):
        ings.setdefault(i.recipe_id, []).append(i.model_dump(exclude={"recipe_id"}))
    steps: dict[int, list[dict]] = {}
    for s in session.exec(select(Step).where(col(Step.recipe_id).in_(ids)).order_by(Step.position)):
        steps.setdefault(s.recipe_id, []).append(s.model_dump(exclude={"recipe_id"}))
    folders = list(session.exec(select(Folder).where(Folder.owner_id == user.id)))
    in_folder: dict[int, list[int]] = {}
    for fr in session.exec(select(FolderRecipe).where(col(FolderRecipe.folder_id).in_([f.id for f in folders]))):
        in_folder.setdefault(fr.folder_id, []).append(fr.recipe_id)

    def rows(model, *where):
        return [r.model_dump(mode="json") for r in session.exec(select(model).where(*where).order_by(model.id))]

    return {
        "exported_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "account": user.model_dump(mode="json"),
        "recipes": [r.model_dump(mode="json") | {"ingredients": ings.get(r.id, []), "steps": steps.get(r.id, [])} for r in recipes],
        "plan": rows(PlanEntry, PlanEntry.owner_id == user.id),
        "grocery_list": rows(GroceryItem, GroceryItem.owner_id == user.id),
        "grocery_templates": rows(GroceryTemplate, GroceryTemplate.owner_id == user.id),
        "favorites": rows(Favorite, Favorite.owner_id == user.id),
        "folders": [f.model_dump(mode="json") | {"recipe_ids": in_folder.get(f.id, [])} for f in folders],
        "foods": rows(Food, Food.owner_id == user.id),
        "imports": rows(ImportJob, ImportJob.owner_id == user.id),
    }


def delete_account(session: Session, user: User) -> None:
    """Remove the user and everything they own. The caller makes sure it isn't the owner."""
    recipe_ids = list(session.exec(select(Recipe.id).where(Recipe.owner_id == user.id)))
    food_ids = list(session.exec(select(Food.id).where(Food.owner_id == user.id)))
    photos = {r for r in session.exec(select(Recipe.image_url).where(Recipe.owner_id == user.id)) if r}
    jobs = list(session.exec(select(ImportJob).where(ImportJob.owner_id == user.id)))

    # Unhook what stays: others' variations of these recipes, recipes using them as a prep, and
    # ingredients or food versions that point at these foods. Done by hand rather than trusting
    # ON DELETE, which older databases may not have.
    session.exec(update(Recipe).where(col(Recipe.parent_id).in_(recipe_ids)).values(parent_id=None))
    session.exec(update(Ingredient).where(col(Ingredient.prep_id).in_(recipe_ids)).values(prep_id=None))
    session.exec(update(Ingredient).where(col(Ingredient.food_id).in_(food_ids)).values(food_id=None))
    session.exec(update(Food).where(col(Food.base_id).in_(food_ids)).values(base_id=None))
    session.exec(update(PlanEntry).where(PlanEntry.owner_id == user.id).values(leftover_of=None))

    session.exec(delete(PlanEntry).where((PlanEntry.owner_id == user.id) | col(PlanEntry.recipe_id).in_(recipe_ids)))
    session.exec(delete(Favorite).where((Favorite.owner_id == user.id) | col(Favorite.recipe_id).in_(recipe_ids)))
    folder_ids = select(Folder.id).where(Folder.owner_id == user.id)
    session.exec(delete(FolderRecipe).where(col(FolderRecipe.folder_id).in_(folder_ids) | col(FolderRecipe.recipe_id).in_(recipe_ids)))
    session.exec(delete(Folder).where(Folder.owner_id == user.id))
    session.exec(delete(GroceryItem).where(GroceryItem.owner_id == user.id))
    session.exec(delete(GroceryTemplate).where(GroceryTemplate.owner_id == user.id))
    session.exec(delete(Ingredient).where(col(Ingredient.recipe_id).in_(recipe_ids)))
    session.exec(delete(Step).where(col(Step.recipe_id).in_(recipe_ids)))
    session.exec(delete(Recipe).where(Recipe.owner_id == user.id))
    session.exec(delete(Food).where(Food.owner_id == user.id))
    session.exec(delete(ImportJob).where(ImportJob.owner_id == user.id))
    session.exec(delete(AuthSession).where(AuthSession.user_id == user.id))
    session.delete(user)
    session.commit()

    # Files last, once the rows are gone: photos no remaining recipe uses (a variation someone
    # else made keeps its photo), and import uploads still waiting.
    still_used = set(session.exec(select(Recipe.image_url).where(col(Recipe.image_url).in_(photos))))
    for url in photos - still_used:
        if url.startswith("/api/images/"):
            path = (IMAGE_DIR / url.rsplit("/", 1)[1]).resolve()
            if path.parent == IMAGE_DIR.resolve():
                path.unlink(missing_ok=True)
    for job in jobs:
        if job.file:
            (UPLOAD_DIR / job.file).unlink(missing_ok=True)

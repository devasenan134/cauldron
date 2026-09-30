from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import JSONResponse
from sqlmodel import Field, Session, SQLModel, col, select

from .. import account

from ..auth import COOKIE, GOOGLE_CLIENT_ID, SESSION_DAYS, AuthError, sign_in, sign_out, verify_google
from ..db import get_session
from ..deps import current_user, is_owner, session_token
from ..importer import ImportJob
from ..models import User

router = APIRouter(prefix="/auth")


class GoogleIn(SQLModel):
    credential: str
    # Apps can't keep cookies well: they ask for the session token in the reply instead.
    want_token: bool = False


class Me(SQLModel):
    email: str
    name: str
    is_owner: bool
    kcal_goal: int
    theme: str
    catalog_view: str = "grid"
    token: str | None = None


class MePatch(SQLModel):
    kcal_goal: int | None = Field(default=None, ge=500, le=10000)
    theme: Literal["system", "light", "dark"] | None = None
    catalog_view: Literal["grid", "list"] | None = None


def me_out(user: User) -> Me:
    return Me(email=user.email, name=user.name, is_owner=is_owner(user), kcal_goal=user.kcal_goal, theme=user.theme,
              catalog_view=user.catalog_view)


@router.get("/config")
def config():
    return {"google_client_id": GOOGLE_CLIENT_ID}


@router.post("/google", response_model=Me)
def google(body: GoogleIn, response: Response, session: Session = Depends(get_session)):
    try:
        user, token = sign_in(session, verify_google(body.credential))
    except AuthError as e:
        raise HTTPException(403, str(e)) from e
    if body.want_token:
        return me_out(user).model_copy(update={"token": token})
    response.set_cookie(COOKIE, token, max_age=SESSION_DAYS * 86400, httponly=True, secure=True, samesite="lax")
    return me_out(user)


@router.get("/me", response_model=Me)
def me(user: User = Depends(current_user)):
    return me_out(user)


@router.patch("/me", response_model=Me)
def update_me(body: MePatch, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Your settings, shared by the website and the app."""
    if body.kcal_goal is not None:
        user.kcal_goal = body.kcal_goal
    if body.theme is not None:
        user.theme = body.theme
    if body.catalog_view is not None:
        user.catalog_view = body.catalog_view
    session.add(user)
    session.commit()
    session.refresh(user)
    return me_out(user)


@router.post("/logout")
def logout(response: Response, session: Session = Depends(get_session), token: str | None = Depends(session_token)):
    sign_out(session, token)
    response.delete_cookie(COOKIE, httponly=True, secure=True, samesite="lax")
    return {"ok": True}


@router.get("/me/export")
def export_me(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """All your data as a JSON file: recipes, plans, lists, foods, folders and imports."""
    name = f"cauldron-{date.today().isoformat()}.json"
    return JSONResponse(account.export(session, user), headers={"Content-Disposition": f'attachment; filename="{name}"'})


@router.delete("/me")
def delete_me(response: Response, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Delete your account and everything in it. There's no undo."""
    if is_owner(user):
        raise HTTPException(403, "the owner's account can't be deleted here: it holds the shared recipes")
    running = session.exec(select(ImportJob).where(ImportJob.owner_id == user.id,
                                                   col(ImportJob.status).not_in(("done", "failed")))).first()
    if running:
        raise HTTPException(409, "An import is still running. Try again when it's done.")
    account.delete_account(session, user)
    response.delete_cookie(COOKIE, httponly=True, secure=True, samesite="lax")
    return {"ok": True}

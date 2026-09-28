from fastapi import APIRouter, Depends, HTTPException, Response
from sqlmodel import Session, SQLModel

from ..auth import COOKIE, GOOGLE_CLIENT_ID, SESSION_DAYS, AuthError, sign_in, sign_out, verify_google
from ..db import get_session
from ..deps import current_user, is_owner, session_token
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
    token: str | None = None


def me_out(user: User) -> Me:
    return Me(email=user.email, name=user.name, is_owner=is_owner(user))


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


@router.post("/logout")
def logout(response: Response, session: Session = Depends(get_session), token: str | None = Depends(session_token)):
    sign_out(session, token)
    response.delete_cookie(COOKIE, httponly=True, secure=True, samesite="lax")
    return {"ok": True}

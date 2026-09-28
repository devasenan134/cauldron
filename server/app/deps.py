from fastapi import Cookie, Depends, Header, HTTPException
from sqlmodel import Session

from .auth import COOKIE, user_for_token
from .db import get_session
from .models import User
from .users import OWNER_EMAIL


def session_token(cookie: str | None = Cookie(default=None, alias=COOKIE),
                  authorization: str | None = Header(default=None)) -> str | None:
    """The website sends the session as a cookie; the Android app as a bearer token."""
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return cookie


def current_user(session: Session = Depends(get_session), token: str | None = Depends(session_token)) -> User:
    user = user_for_token(session, token)
    if user is None:
        raise HTTPException(401, "sign in required")
    return user


def is_owner(user: User) -> bool:
    return user.email == OWNER_EMAIL

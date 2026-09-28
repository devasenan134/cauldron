from fastapi import Cookie, Depends, HTTPException
from sqlmodel import Session

from .auth import COOKIE, user_for_token
from .db import get_session
from .models import User
from .users import OWNER_EMAIL


def current_user(session: Session = Depends(get_session),
                 token: str | None = Cookie(default=None, alias=COOKIE)) -> User:
    user = user_for_token(session, token)
    if user is None:
        raise HTTPException(401, "sign in required")
    return user


def is_owner(user: User) -> bool:
    return user.email == OWNER_EMAIL

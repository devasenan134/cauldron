from fastapi import Depends
from sqlmodel import Session

from .db import get_session
from .models import User
from .users import owner


def current_user(session: Session = Depends(get_session)) -> User:
    return owner(session)

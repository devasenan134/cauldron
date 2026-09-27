import os

from sqlmodel import Session, select

from .models import User

# Single-user for now. Google sign-in (later) will replace this with the
# signed-in user; every row already carries an owner so nothing else changes.
OWNER_EMAIL = os.environ.get("CAULDRON_OWNER_EMAIL", "owner@localhost")


def owner(session: Session) -> User:
    user = session.exec(select(User).where(User.email == OWNER_EMAIL)).first()
    if user is None:
        user = User(email=OWNER_EMAIL, name="Owner")
        session.add(user)
        session.commit()
        session.refresh(user)
    return user

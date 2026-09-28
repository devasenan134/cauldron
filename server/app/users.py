import os

from sqlmodel import Session, select

from .models import User

# The owner curates the shared Cook Well library and can always sign in.
OWNER_EMAIL = os.environ.get("CAULDRON_OWNER_EMAIL", "owner@localhost").strip().lower()
# Before sign-in existed, everything belonged to this placeholder user.
PLACEHOLDER_EMAIL = "owner@localhost"


def allowed_emails() -> set[str]:
    extra = os.environ.get("CAULDRON_ALLOWED_EMAILS", "")
    return {OWNER_EMAIL} | {e.strip().lower() for e in extra.split(",") if e.strip()}


def owner(session: Session) -> User:
    user = session.exec(select(User).where(User.email == OWNER_EMAIL)).first()
    if user is None:
        user = User(email=OWNER_EMAIL, name="Owner")
        session.add(user)
        session.commit()
        session.refresh(user)
    return user


def claim_placeholder(session: Session) -> None:
    """Give the pre-sign-in placeholder user (and all its rows) the owner's real email."""
    if OWNER_EMAIL == PLACEHOLDER_EMAIL:
        return
    placeholder = session.exec(select(User).where(User.email == PLACEHOLDER_EMAIL)).first()
    taken = session.exec(select(User).where(User.email == OWNER_EMAIL)).first()
    if placeholder and not taken:
        placeholder.email = OWNER_EMAIL
        session.add(placeholder)
        session.commit()

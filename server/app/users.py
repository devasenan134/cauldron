import os

from sqlmodel import Session, select

from .models import User

# The owner curates the shared recipe library and can always sign in.
OWNER_EMAIL = os.environ.get("CAULDRON_OWNER_EMAIL", "owner@localhost").strip().lower()
# Before sign-in existed, everything belonged to this placeholder user.
PLACEHOLDER_EMAIL = "owner@localhost"
# Who may sign in: "invite" = the owner and CAULDRON_ALLOWED_EMAILS only; "open" = any Google account.
SIGNUP = os.environ.get("CAULDRON_SIGNUP", "invite").strip().lower()
# Who sees the shared library: "guests" = the owner and CAULDRON_ALLOWED_EMAILS; "everyone" = all users.
# The Cook Well import is someone else's work, so on a public server keep it to guests.
LIBRARY = os.environ.get("CAULDRON_LIBRARY", "guests").strip().lower()


def _emails(name: str) -> set[str]:
    return {e.strip().lower() for e in os.environ.get(name, "").split(",") if e.strip()}


def allowed_emails() -> set[str]:
    return {OWNER_EMAIL} | _emails("CAULDRON_ALLOWED_EMAILS")


def blocked_emails() -> set[str]:
    return _emails("CAULDRON_BLOCKED_EMAILS")


def may_sign_in(email: str) -> bool:
    """Blocked accounts never; otherwise anyone on an open server, or the guest list."""
    if email in blocked_emails():
        return False
    return SIGNUP == "open" or email in allowed_emails()


def sees_library(user: User) -> bool:
    return LIBRARY == "everyone" or user.email in allowed_emails()


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

import hashlib
import os
import secrets
from datetime import datetime, timedelta, timezone

from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from sqlmodel import Session, select

from .models import AuthSession, User
from .users import blocked_emails, may_sign_in

GOOGLE_CLIENT_ID = os.environ.get("CAULDRON_GOOGLE_CLIENT_ID", "")
COOKIE = "cauldron_session"
SESSION_DAYS = 30

_google = google_requests.Request()


class AuthError(Exception):
    pass


def verify_google(credential: str) -> dict:
    """Check a Google Identity Services ID token; returns its claims."""
    if not GOOGLE_CLIENT_ID:
        raise AuthError("Google sign-in is not configured")
    try:
        claims = id_token.verify_oauth2_token(credential, _google, GOOGLE_CLIENT_ID)
    except ValueError as e:
        raise AuthError(f"invalid Google token: {e}") from e
    if not claims.get("email_verified"):
        raise AuthError("Google email is not verified")
    return claims


def sign_in(session: Session, claims: dict) -> tuple[User, str]:
    """Find or create the user for verified Google claims and start a session."""
    email = claims["email"].lower()
    if not may_sign_in(email):
        raise AuthError(f"{email} can't sign in here" if email in blocked_emails() else f"{email} is not on the guest list")
    user = session.exec(select(User).where(User.email == email)).first()
    if user is None:
        user = User(email=email)
    user.name = claims.get("name") or user.name
    session.add(user)
    session.flush()
    token = secrets.token_urlsafe(32)
    session.add(AuthSession(token_hash=_hash(token), user_id=user.id,
                            expires_at=datetime.now(timezone.utc) + timedelta(days=SESSION_DAYS)))
    session.commit()
    session.refresh(user)
    return user, token


def user_for_token(session: Session, token: str | None) -> User | None:
    if not token:
        return None
    row = session.exec(select(AuthSession).where(AuthSession.token_hash == _hash(token))).first()
    if row is None:
        return None
    if row.expires_at.replace(tzinfo=timezone.utc) < datetime.now(timezone.utc):
        session.delete(row)
        session.commit()
        return None
    user = session.get(User, row.user_id)
    # Removing someone from the guest list (or blocking them) locks them out on their next request.
    return user if user and may_sign_in(user.email) else None


def sign_out(session: Session, token: str | None) -> None:
    if token:
        row = session.exec(select(AuthSession).where(AuthSession.token_hash == _hash(token))).first()
        if row:
            session.delete(row)
            session.commit()


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()

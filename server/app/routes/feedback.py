"""Bug reports and feature requests, sent from Settings on the website or in the app.

Everyone can send them (10 a day, the owner has no limit) and sees their own. The owner sees
everyone's, newest first, and marks them done.
"""
from datetime import timedelta
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Field, Session, SQLModel, col, func, select

from ..db import get_session
from ..deps import current_user, is_owner
from ..models import Feedback, User, now

router = APIRouter()

PER_DAY = 10


class FeedbackIn(SQLModel):
    type: Literal["bug", "feature"]
    title: str = Field(max_length=200)
    body: str = Field(default="", max_length=5000)
    meta: dict[str, str] = {}


class FeedbackOut(SQLModel):
    id: int
    type: str
    title: str
    body: str
    meta: dict
    status: str
    created_at: str
    user_name: str | None = None  # for the owner: who sent it
    user_email: str | None = None


class StatusIn(SQLModel):
    status: Literal["open", "done"]


def feedback_out(f: Feedback, user: User | None = None) -> FeedbackOut:
    return FeedbackOut(**f.model_dump(exclude={"created_at", "user_id"}), created_at=f.created_at.isoformat(),
                       user_name=user.name if user else None, user_email=user.email if user else None)


@router.post("/feedback", response_model=FeedbackOut)
def send(body: FeedbackIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    if not body.title.strip():
        raise HTTPException(400, "Give it a short title.")
    if not is_owner(user):
        today = session.exec(select(func.count()).select_from(Feedback).where(
            Feedback.user_id == user.id, Feedback.created_at > now() - timedelta(days=1))).one()
        if today >= PER_DAY:
            raise HTTPException(429, f"That's {PER_DAY} reports today, the most one person can send. Try again tomorrow.")
    # A few short labels only (version, platform, device): nothing else rides along.
    meta = {k[:40]: v[:200] for k, v in list(body.meta.items())[:10]}
    f = Feedback(user_id=user.id, type=body.type, title=body.title.strip(), body=body.body.strip(), meta=meta)
    session.add(f)
    session.commit()
    session.refresh(f)
    return feedback_out(f)


@router.get("/feedback", response_model=list[FeedbackOut])
def list_feedback(session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Yours; for the owner, everyone's (open ones first, then newest first)."""
    stmt = select(Feedback, User).join(User, User.id == Feedback.user_id)
    if not is_owner(user):
        stmt = stmt.where(Feedback.user_id == user.id)
    rows = session.exec(stmt.order_by(Feedback.status == "done", col(Feedback.created_at).desc()).limit(500))
    return [feedback_out(f, u if is_owner(user) else None) for f, u in rows]


@router.patch("/feedback/{feedback_id}", response_model=FeedbackOut)
def set_status(feedback_id: int, body: StatusIn, session: Session = Depends(get_session), user: User = Depends(current_user)):
    if not is_owner(user):
        raise HTTPException(403, "only the owner can do that")
    f = session.get(Feedback, feedback_id)
    if f is None:
        raise HTTPException(404, "feedback not found")
    f.status = body.status
    session.add(f)
    session.commit()
    return feedback_out(f, session.get(User, f.user_id))

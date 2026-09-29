from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from sqlmodel import Session, SQLModel

from .. import importer
from ..db import get_session
from ..deps import current_user
from ..models import User

router = APIRouter(prefix="/import")


class ImportIn(SQLModel):
    url: str


class JobOut(SQLModel):
    id: int
    url: str
    status: str  # queued | fetching | reading | saving | done | failed
    message: str
    recipe_id: int | None


@router.get("/status")
def status(user: User = Depends(current_user)):
    """Whether importing works on this server, and whether Instagram cookies are set."""
    return {"ready": bool(importer.GEMINI_KEY), "instagram_cookies": Path(importer.COOKIES).is_file()}


@router.post("", response_model=JobOut)
def start(body: ImportIn, tasks: BackgroundTasks, session: Session = Depends(get_session), user: User = Depends(current_user)):
    """Start importing a video; poll GET /import/{id}. Already imported? Returns that recipe at once."""
    import re
    m = re.search(r"https?://\S+", body.url)
    if not m or importer.platform(m[0]) is None:
        raise HTTPException(400, "Paste a YouTube or Instagram Reel link.")
    url = importer.clean_url(m[0])
    if existing := importer.find_existing(session, user.id, url):
        job = importer.ImportJob(owner_id=user.id, url=url, status="done", message=existing.title, recipe_id=existing.id)
        session.add(job)
        session.commit()
        session.refresh(job)
        return job
    job = importer.ImportJob(owner_id=user.id, url=url)
    session.add(job)
    session.commit()
    session.refresh(job)
    tasks.add_task(importer.run, job.id)
    return job


@router.get("/{job_id}", response_model=JobOut)
def get(job_id: int, session: Session = Depends(get_session), user: User = Depends(current_user)):
    job = session.get(importer.ImportJob, job_id)
    if job is None or job.owner_id != user.id:
        raise HTTPException(404, "import not found")
    return job

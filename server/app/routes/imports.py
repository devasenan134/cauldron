from pathlib import Path

import re
import secrets

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
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
    """Start importing a video or a recipe page; poll GET /import/{id}. Already imported? Returns that recipe at once."""
    m = re.search(r"https?://\S+", body.url)
    if not m or importer.platform(m[0]) is None:
        raise HTTPException(400, "Paste a link to a recipe page, a YouTube video or an Instagram Reel.")
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


@router.post("/file", response_model=JobOut)
async def start_file(request: Request, tasks: BackgroundTasks, name: str = "", session: Session = Depends(get_session),
                     user: User = Depends(current_user)):
    """Import a PDF, a photo of a recipe, or a recipe file (YAML, JSON, text): the file is the request
    body, ?name= its file name. Poll GET /import/{id} like a link."""
    mime = request.headers.get("content-type", "").split(";")[0].strip().lower()
    suffix = name.rsplit(".", 1)[-1].lower() if "." in name else ""
    ext = importer.FILE_TYPES.get(mime) or (suffix if suffix in importer.TEXT_EXTS or suffix in importer.FILE_TYPES.values() else None)
    if ext is None and (mime.startswith("text/") or mime in ("application/json", "application/yaml", "application/x-yaml")):
        ext = "txt"
    if ext is None:
        raise HTTPException(415, "Send a PDF, a photo, or a recipe file (YAML, JSON or text).")
    data = await request.body()
    if not data:
        raise HTTPException(400, "The file is empty.")
    if len(data) > importer.FILE_MAX:
        raise HTTPException(413, "That file is too big (40 MB at most).")
    importer.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    stored = f"{secrets.token_urlsafe(12)}.{ext}"
    (importer.UPLOAD_DIR / stored).write_bytes(data)
    job = importer.ImportJob(owner_id=user.id, url=(name.strip() or f"upload.{ext}")[:200], file=stored)
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

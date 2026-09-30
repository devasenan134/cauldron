import os
import secrets
from collections import Counter
from datetime import date
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse

from ..db import DB_PATH
from ..deps import current_user, is_owner
from ..models import User

# Photos people upload for their own recipes, in data/images/.
IMAGE_DIR = Path(os.environ.get("CAULDRON_IMAGE_DIR", DB_PATH.parent / "images"))
TYPES = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}
MAX_BYTES = 10 * 1024 * 1024
# Photos per person per day (the owner has no limit), so nobody can fill the disk.
PER_DAY = int(os.environ.get("CAULDRON_UPLOADS_PER_DAY", "50"))
_uploads: Counter[tuple[int, date]] = Counter()

# Uploading needs a signed-in user; viewing doesn't (names are unguessable, and the app's image
# loader and <img> tags can then fetch them like any other photo).
upload_router = APIRouter(dependencies=[Depends(current_user)])
public_router = APIRouter()


@upload_router.post("/images")
async def upload(request: Request, user: User = Depends(current_user)):
    """The photo is the request body (Content-Type image/jpeg, image/png or image/webp)."""
    ext = TYPES.get(request.headers.get("content-type", "").split(";")[0].strip())
    if ext is None:
        raise HTTPException(415, "send a JPEG, PNG or WebP photo")
    data = await request.body()
    if not data:
        raise HTTPException(400, "empty photo")
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "photo is too big (10 MB at most)")
    key = (user.id, date.today())
    if not is_owner(user) and _uploads[key] >= PER_DAY:
        raise HTTPException(429, f"That's {PER_DAY} photos today, the most one person can add. Try again tomorrow.")
    _uploads[key] += 1
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    name = f"{secrets.token_urlsafe(16)}.{ext}"
    (IMAGE_DIR / name).write_bytes(data)
    return {"url": f"/api/images/{name}"}


@public_router.get("/images/{name}")
def image(name: str):
    path = (IMAGE_DIR / name).resolve()
    if path.parent != IMAGE_DIR.resolve() or not path.is_file():
        raise HTTPException(404, "no such photo")
    return FileResponse(path, headers={"Cache-Control": "public, max-age=31536000, immutable"})

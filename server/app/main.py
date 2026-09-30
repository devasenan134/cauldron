import os
import threading
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from sqlmodel import Session

from . import starter
from .db import engine, init_db
from .deps import current_user
from .routes import app_updates, auth, catalog, feedback, foods, grocery, images, imports, libraries, planner, recipes
from .users import claim_placeholder

# Built website (web/dist); served at / when present.
WEB_DIST = Path(os.environ.get("CAULDRON_WEB", Path(__file__).resolve().parents[2] / "web" / "dist"))


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    with Session(engine) as session:
        claim_placeholder(session)
        starter.seed(session)  # adds the starter recipes that are missing
    threading.Thread(target=_starter_photos, daemon=True).start()
    yield


def _starter_photos() -> None:
    """Download the starter recipes' photos in the background (they're linked from Wikimedia until then)."""
    with Session(engine) as session:
        starter.fetch_photos(session)


app = FastAPI(title="Cauldron", lifespan=lifespan)
app.include_router(auth.router, prefix="/api")
app.include_router(images.public_router, prefix="/api")
app.include_router(images.upload_router, prefix="/api")
# Everything else needs a signed-in user.
for module in (recipes, foods, planner, grocery, app_updates, catalog, imports, feedback, libraries):
    app.include_router(module.router, prefix="/api", dependencies=[Depends(current_user)])


@app.get("/api/health")
def health():
    return {"ok": True}


if WEB_DIST.is_dir():
    app.mount("/assets", StaticFiles(directory=WEB_DIST / "assets"), name="assets")

    @app.get("/{path:path}", include_in_schema=False)
    def spa(path: str):
        """Serve the website; unknown paths get index.html so client routes work on reload."""
        if path.startswith("api/"):
            raise HTTPException(404)
        file = (WEB_DIST / path).resolve()
        if path and file.is_file() and WEB_DIST.resolve() in file.parents:
            return FileResponse(file)
        return FileResponse(WEB_DIST / "index.html")

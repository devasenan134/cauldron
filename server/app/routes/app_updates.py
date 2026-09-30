import os
import re
from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from sqlmodel import SQLModel

from ..db import DB_PATH

router = APIRouter(prefix="/app")

# Android app releases: cauldron-<version>.apk, with optional notes in cauldron-<version>.md.
# The app updates itself from its own server, so self-hosted builds follow their server's releases.
APK_DIR = Path(os.environ.get("CAULDRON_APK_DIR", DB_PATH.parent / "apk"))
APK_NAME = re.compile(r"^cauldron-(\d+(?:\.\d+)*)\.apk$")


class AppRelease(SQLModel):
    version: str
    notes: str
    size: int


def version_key(version: str) -> tuple[int, ...]:
    return tuple(int(p) for p in version.split("."))


def releases() -> dict[str, Path]:
    if not APK_DIR.is_dir():
        return {}
    return {m[1]: f for f in APK_DIR.iterdir() if (m := APK_NAME.match(f.name))}


@router.get("/latest", response_model=AppRelease | None)
def latest():
    """The newest app version on the server (null if none)."""
    found = releases()
    if not found:
        return None
    version = max(found, key=version_key)
    apk = found[version]
    notes = apk.with_suffix(".md")
    return AppRelease(version=version, notes=notes.read_text().strip() if notes.is_file() else "", size=apk.stat().st_size)


@router.get("/releases", response_model=list[AppRelease])
def all_releases():
    """Every app version on the server, newest first, with its notes."""
    found = releases()
    out = []
    for version in sorted(found, key=version_key, reverse=True):
        notes = found[version].with_suffix(".md")
        out.append(AppRelease(version=version, notes=notes.read_text().strip() if notes.is_file() else "", size=found[version].stat().st_size))
    return out


@router.get("/download/{version}")
def download(version: str):
    apk = releases().get(version)
    if apk is None:
        raise HTTPException(404, "no such version")
    return FileResponse(apk, media_type="application/vnd.android.package-archive", filename=apk.name)

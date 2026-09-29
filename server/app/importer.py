"""Import a recipe from a video: YouTube (videos and Shorts) and Instagram Reels.

1. yt-dlp reads the video's page: title, description, creator, thumbnail, length.
2. Gemini watches the video (sound and on-screen text) and reads the description, and writes the
   recipe in the library's shape: sections, ingredients with amounts as written, steps, servings,
   time, cuisine, meal, tags. Quantities and macros in the description win over what's said, since
   creators often put the exact numbers there. Macros the creator states are kept as given.
3. save_recipe() links foods and works out weights, like any recipe you write, so calories and
   macros are calculated even when the creator gives none.

YouTube links go to Gemini as they are. Instagram Reels are downloaded (small) and sent inline, or
through the Files API when large. Imports run one at a time in the background; the app polls.
"""
import base64
import json
import logging
import os
import re
import tempfile
import threading
import time
from datetime import datetime
from pathlib import Path

import httpx
from sqlmodel import Field, Session, SQLModel, select

from .db import DB_PATH, engine
from .models import Recipe, now
from .recipe_edit import IngredientIn, RecipeIn, StepIn, save_recipe

log = logging.getLogger("cauldron.import")

GEMINI_KEY = os.environ.get("CAULDRON_GEMINI_KEY", "")
GEMINI_MODEL = os.environ.get("CAULDRON_GEMINI_MODEL", "gemini-3.8-flash")
GEMINI_FALLBACK = os.environ.get("CAULDRON_GEMINI_FALLBACK", "gemini-3.5-flash-lite")  # when the main one is rate-limited
GEMINI = "https://generativelanguage.googleapis.com"
# Netscape cookies file exported from a browser signed in to Instagram (Reels often need it).
COOKIES = os.environ.get("CAULDRON_COOKIES", str(DB_PATH.parent / "cookies.txt"))
IMAGE_DIR = Path(os.environ.get("CAULDRON_IMAGE_DIR", DB_PATH.parent / "images"))
INLINE_MAX = 15 * 1024 * 1024  # bigger videos go through the Files API
MAX_SECONDS = 45 * 60

_one_at_a_time = threading.Lock()


class ImportJob(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    owner_id: int = Field(foreign_key="user.id", index=True)
    url: str
    status: str = "queued"  # queued | fetching | reading | saving | done | failed
    message: str = ""
    recipe_id: int | None = None
    created_at: datetime = Field(default_factory=now)


class ImportError_(Exception):
    """A reason to show the person ("That video isn't a recipe")."""


def platform(url: str) -> str | None:
    if re.search(r"(youtube\.com|youtu\.be)/", url):
        return "youtube"
    if re.search(r"instagram\.com/(reel|reels|p|tv)/", url):
        return "instagram"
    return None


def clean_url(url: str) -> str:
    """Strip tracking bits so the same video is recognised however it was shared."""
    url = url.strip()
    if m := re.search(r"youtu\.be/([\w-]{11})", url):
        return f"https://www.youtube.com/watch?v={m[1]}"
    if m := re.search(r"youtube\.com/shorts/([\w-]{11})", url):
        return f"https://www.youtube.com/shorts/{m[1]}"
    if m := re.search(r"youtube\.com/watch\?.*?v=([\w-]{11})", url):
        return f"https://www.youtube.com/watch?v={m[1]}"
    if m := re.search(r"instagram\.com/(?:[\w.]+/)?(reel|reels|p|tv)/([\w-]+)", url):
        return f"https://www.instagram.com/{'reel' if m[1] == 'reels' else m[1]}/{m[2]}/"
    return url


def run(job_id: int) -> None:
    """Background worker: import one job, recording progress on it as it goes."""
    with _one_at_a_time, Session(engine) as session:
        job = session.get(ImportJob, job_id)

        def step(status: str, message: str = "") -> None:
            job.status, job.message = status, message
            session.add(job)
            session.commit()

        try:
            recipe = import_video(session, job, step)
            job.recipe_id = recipe.id
            step("done", recipe.title)
        except ImportError_ as e:
            step("failed", str(e))
        except Exception as e:  # noqa: BLE001 — anything else: keep the worker alive, say what broke
            log.exception("import %s failed", job.url)
            step("failed", f"Something went wrong: {e}")


def import_video(session: Session, job: ImportJob, step) -> Recipe:
    if not GEMINI_KEY:
        raise ImportError_("Importing isn't set up on the server yet (no Gemini key).")
    kind = platform(job.url)
    if kind is None:
        raise ImportError_("Paste a YouTube or Instagram Reel link.")

    step("fetching", "Reading the video's page")
    with tempfile.TemporaryDirectory() as tmp:
        info = video_info(job.url, kind, tmp)
        if (info.get("duration") or 0) > MAX_SECONDS:
            raise ImportError_("That video is too long to import (45 minutes at most).")
        media = None if kind == "youtube" else download(job.url, tmp)

        step("reading", "Watching the video and writing the recipe")
        data = gemini_recipe(info, kind, job.url, media)

    if not data.get("is_recipe") or not data.get("ingredients"):
        raise ImportError_("I couldn't find a recipe in that video.")

    step("saving", "Working out calories")
    return save_import(session, job, info, data)


# --- the video's page

def ytdlp_opts(kind: str, tmp: str) -> dict:
    opts = {"quiet": True, "no_warnings": True, "noplaylist": True, "paths": {"home": tmp}, "outtmpl": "video.%(ext)s"}
    if kind == "instagram" and Path(COOKIES).is_file():
        opts["cookiefile"] = COOKIES
    return opts


def video_info(url: str, kind: str, tmp: str) -> dict:
    from yt_dlp import YoutubeDL
    from yt_dlp.utils import DownloadError

    try:
        with YoutubeDL(ytdlp_opts(kind, tmp)) as ydl:
            return ydl.extract_info(url, download=False)
    except DownloadError as e:
        text = str(e).lower()
        if kind == "instagram" and ("login" in text or "cookies" in text or "rate" in text):
            raise ImportError_("Instagram wants a login for that Reel. The server needs Instagram cookies (see Settings).") from e
        if "private" in text or "unavailable" in text:
            raise ImportError_("That video is private or unavailable.") from e
        raise ImportError_(f"Couldn't open that video ({str(e).split(':')[-1].strip()[:120]}).") from e


def download(url: str, tmp: str) -> Path:
    """The Reel itself, small: under 720p, with sound."""
    from yt_dlp import YoutubeDL

    opts = ytdlp_opts("instagram", tmp) | {"format": "best[height<=720][ext=mp4]/best[ext=mp4]/best"}
    with YoutubeDL(opts) as ydl:
        ydl.download([url])
    files = list(Path(tmp).glob("video.*"))
    if not files:
        raise ImportError_("Couldn't download that Reel.")
    return files[0]


# --- Gemini

PROMPT = """You turn cooking videos into recipes for a recipe app.

Watch the video (listen to what's said and read any text on screen) and read the video's
description below. Write the recipe that's cooked in it.

Rules:
- The description often has the exact quantities, ingredient list, servings and macros. When the
  description and the video disagree, trust the description.
- Write each ingredient's amount the way a recipe would ("200 g", "2 tbsp", "3 cloves", "1 can",
  "a drizzle", "to taste"). Keep the creator's units. Leave the amount empty only if it's never given
  and can't be seen.
- Group ingredients into sections when the recipe has parts ("Marinade", "Sauce", "To serve").
- Steps: short, clear instructions in order, each with an optional short title.
- servings: how many portions it makes (a number). total_minutes: the total time if said or clear.
- category: one of Breakfast, Lunch, Dinner, Side, Snack, Dessert, Drink. cuisine: e.g. Indian, Mexican.
- tags: pick any that fit from: Easy, Level Up, Quick, Under 1 Hour, I Got Time, Chicken, Beef, Pork,
  Eggs, Seafood, Vegetarian, Stir Fry, Bake, Braise, Sear, Crispy, Grill, High Protein, Gluten Free,
  Low Fat, Low Carb, Dairy Free, Meal Prep.
- stated_nutrition: ONLY numbers the creator actually gives (in the description, on screen or said).
  Never estimate them yourself. Say whether they are per serving or for the whole recipe.
- If the video isn't a recipe (a vlog, a review…), set is_recipe to false.
- Write in English.

Video title: {title}
Creator: {creator}

Description:
{description}
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "is_recipe": {"type": "boolean"},
        "title": {"type": "string", "description": "A short, clear recipe name (not the video's clickbait title)."},
        "description": {"type": "string", "description": "One or two sentences about the dish."},
        "servings": {"type": ["number", "null"]},
        "yield_text": {"type": ["string", "null"], "description": "Yield as said, e.g. '4 wraps'."},
        "total_minutes": {"type": ["integer", "null"]},
        "category": {"type": ["string", "null"]},
        "cuisine": {"type": ["string", "null"]},
        "tags": {"type": "array", "items": {"type": "string"}},
        "ingredients": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "section": {"type": ["string", "null"]},
                    "name": {"type": "string"},
                    "amount": {"type": "string", "description": "As written: '200 g', '2 tbsp', 'to taste', or ''."},
                    "note": {"type": "string", "description": "Prep, e.g. 'diced'. Or ''."},
                },
                "required": ["name", "amount", "note"],
            },
        },
        "steps": {
            "type": "array",
            "items": {"type": "object", "properties": {"title": {"type": "string"}, "text": {"type": "string"}}, "required": ["text"]},
        },
        "stated_nutrition": {
            "type": ["object", "null"],
            "properties": {
                "per": {"type": "string", "enum": ["serving", "recipe"]},
                "calories": {"type": ["number", "null"]},
                "protein": {"type": ["number", "null"]},
                "carbohydrates": {"type": ["number", "null"]},
                "fat": {"type": ["number", "null"]},
            },
        },
    },
    "required": ["is_recipe", "title", "ingredients", "steps"],
}


def gemini_recipe(info: dict, kind: str, url: str, media: Path | None) -> dict:
    prompt = PROMPT.format(title=info.get("title") or "", creator=info.get("uploader") or info.get("channel") or "",
                           description=(info.get("description") or "(none)")[:6000])
    with httpx.Client(timeout=httpx.Timeout(300, connect=20), headers={"x-goog-api-key": GEMINI_KEY}) as http:
        if kind == "youtube":
            video = {"file_data": {"file_uri": info.get("webpage_url") or url}}
        elif media.stat().st_size <= INLINE_MAX:
            video = {"inline_data": {"mime_type": "video/mp4", "data": base64.b64encode(media.read_bytes()).decode()}}
        else:
            video = {"file_data": {"file_uri": upload(http, media), "mime_type": "video/mp4"}}
        body = {
            "contents": [{"role": "user", "parts": [video, {"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json", "responseJsonSchema": SCHEMA, "temperature": 0.2,
                                 # a long video at full detail would use a lot of the free quota
                                 "mediaResolution": "MEDIA_RESOLUTION_LOW"},
        }
        last = None
        for model in dict.fromkeys([GEMINI_MODEL, GEMINI_FALLBACK]):
            for attempt in range(3):
                r = http.post(f"{GEMINI}/v1beta/models/{model}:generateContent", json=body)
                if r.status_code == 200:
                    return parse(r.json())
                last = r
                if r.status_code == 429 or r.status_code >= 500:
                    time.sleep(4 * (attempt + 1))
                    if r.status_code == 429 and attempt == 1:
                        break  # quota for this model: try the fallback
                    continue
                break
        detail = ""
        try:
            detail = last.json()["error"]["message"]
        except Exception:  # noqa: BLE001
            detail = last.text[:200] if last is not None else ""
        if last is not None and last.status_code == 429:
            raise ImportError_("The free Gemini quota is used up for now. Try again later.")
        raise ImportError_(f"Gemini couldn't read the video ({detail[:160]}).")


def upload(http: httpx.Client, path: Path) -> str:
    """Files API: resumable upload, then wait until the file is ready."""
    size = path.stat().st_size
    start = http.post(f"{GEMINI}/upload/v1beta/files", json={"file": {"display_name": path.name}}, headers={
        "X-Goog-Upload-Protocol": "resumable", "X-Goog-Upload-Command": "start",
        "X-Goog-Upload-Header-Content-Length": str(size), "X-Goog-Upload-Header-Content-Type": "video/mp4"})
    start.raise_for_status()
    done = http.post(start.headers["x-goog-upload-url"], content=path.read_bytes(), headers={
        "Content-Length": str(size), "X-Goog-Upload-Offset": "0", "X-Goog-Upload-Command": "upload, finalize"})
    done.raise_for_status()
    f = done.json()["file"]
    for _ in range(60):
        if f.get("state") == "ACTIVE":
            return f["uri"]
        if f.get("state") == "FAILED":
            raise ImportError_("Gemini couldn't process that video file.")
        time.sleep(2)
        f = http.get(f"{GEMINI}/v1beta/{f['name']}").json()
    raise ImportError_("Gemini took too long to process the video.")


def parse(resp: dict) -> dict:
    try:
        text = "".join(p.get("text", "") for p in resp["candidates"][0]["content"]["parts"])
        return json.loads(text)
    except (KeyError, IndexError, json.JSONDecodeError) as e:
        reason = (resp.get("candidates") or [{}])[0].get("finishReason") or resp.get("promptFeedback", {}).get("blockReason")
        raise ImportError_(f"Gemini's answer wasn't a recipe ({reason or e}).") from e


# --- saving

def save_import(session: Session, job: ImportJob, info: dict, data: dict) -> Recipe:
    kind = platform(job.url)
    stated = data.get("stated_nutrition") or {}
    source_nutrition = None
    if any(stated.get(k) for k in ("calories", "protein", "carbohydrates", "fat")):
        source_nutrition = {k: stated.get(k) for k in ("calories", "protein", "carbohydrates", "fat") if stated.get(k) is not None}
        source_nutrition |= {"per": stated.get("per") or "serving", "from": "creator"}
    body = RecipeIn(
        title=(data.get("title") or info.get("title") or "Imported recipe")[:120],
        description=data.get("description") or "",
        image_url=keep_thumbnail(info),
        video_url=job.url,
        source_url=job.url,
        servings=data.get("servings") or None,
        yield_text=data.get("yield_text") or None,
        total_minutes=data.get("total_minutes") or None,
        cuisine=data.get("cuisine") or None,
        category=data.get("category") or None,
        tags=[t for t in data.get("tags") or [] if t][:8],
        ingredients=[IngredientIn(group=i.get("section") or None, name=i["name"], note=i.get("note") or "", label=i.get("amount") or "")
                     for i in data["ingredients"] if i.get("name", "").strip()],
        steps=[StepIn(title=s.get("title") or "", text=s["text"]) for s in data.get("steps") or [] if s.get("text", "").strip()],
    )
    recipe = Recipe(owner_id=job.owner_id, source=kind, title=body.title, slug="",
                    author=info.get("uploader") or info.get("channel"), source_nutrition=source_nutrition)
    recipe = save_recipe(session, recipe, body)
    recipe.source, recipe.author, recipe.source_nutrition = kind, info.get("uploader") or info.get("channel"), source_nutrition
    session.add(recipe)
    session.commit()
    session.refresh(recipe)
    return recipe


def keep_thumbnail(info: dict) -> str | None:
    """Save the video's cover photo (Instagram's links expire) and use it as the recipe's photo."""
    url = info.get("thumbnail")
    if not url:
        return None
    try:
        r = httpx.get(url, timeout=20, follow_redirects=True)
        r.raise_for_status()
        ext = {"image/png": "png", "image/webp": "webp"}.get(r.headers.get("content-type", "").split(";")[0], "jpg")
        IMAGE_DIR.mkdir(parents=True, exist_ok=True)
        name = f"{os.urandom(12).hex()}.{ext}"
        (IMAGE_DIR / name).write_bytes(r.content)
        return f"/api/images/{name}"
    except Exception:  # noqa: BLE001 — a recipe without a photo is fine
        return url if "ytimg" in url else None


def find_existing(session: Session, owner_id: int, url: str) -> Recipe | None:
    return session.exec(select(Recipe).where(Recipe.owner_id == owner_id, Recipe.source_url == url)).first()

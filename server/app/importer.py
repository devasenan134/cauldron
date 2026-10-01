"""Import a recipe from a video (YouTube videos and Shorts, Instagram Reels), a recipe web page,
or a file (a PDF, or a photo of a cookbook page or recipe card).

Videos:
1. yt-dlp reads the video's page: title, description, creator, thumbnail, length.
2. Gemini watches the video (sound and on-screen text) and reads the description, and writes the
   recipe in the library's shape: sections, ingredients with amounts as written, steps, servings,
   time, cuisine, meal, tags. Quantities and macros in the description win over what's said, since
   creators often put the exact numbers there. Macros the creator states are kept as given.
3. save_recipe() links foods and works out weights, like any recipe you write, so calories and
   macros are calculated even when the creator gives none.

YouTube links go to Gemini as they are. Instagram Reels are downloaded (small) and sent inline, or
through the Files API when large.

Web pages: the server reads the page itself and hands Gemini the site's schema.org Recipe data
(most recipe sites publish it) plus the page's text, so Gemini skips the life story and the ads.
A link straight to a PDF is imported like an uploaded PDF.

Files: uploaded to POST /import/file, kept in data/imports/ until the import is done, and sent to
Gemini as they are (Gemini reads PDFs and photos natively).

Imports run one at a time in the background; the app polls.
"""
import base64
import html
import ipaddress
import json
import logging
import os
import re
import socket
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
UPLOAD_DIR = Path(os.environ.get("CAULDRON_IMPORT_DIR", DB_PATH.parent / "imports"))
INLINE_MAX = 15 * 1024 * 1024  # bigger videos and files go through the Files API
FILE_MAX = 40 * 1024 * 1024
FILE_TYPES = {"application/pdf": "pdf", "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp",
              "image/heic": "heic", "image/heif": "heif"}
# Recipe files other apps export (Mealie, Paprika, Tandoor…) or plain notes: read as text.
TEXT_EXTS = {"yaml", "yml", "json", "txt", "md", "markdown", "xml", "csv"}
TEXT_MAX = 400_000  # characters
# Some recipe sites turn away anything that doesn't look like a browser.
BROWSER = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36",
           "Accept": "text/html,application/xhtml+xml,application/pdf;q=0.9,*/*;q=0.8", "Accept-Language": "en;q=0.9"}
MAX_SECONDS = 45 * 60

_one_at_a_time = threading.Lock()


class ImportJob(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    owner_id: int = Field(foreign_key="user.id", index=True)
    url: str
    status: str = "queued"  # queued | fetching | reading | saving | done | failed
    message: str = ""
    recipe_id: int | None = None
    file: str | None = None  # an uploaded file's name in data/imports/ (url then holds its original name)
    created_at: datetime = Field(default_factory=now)


class ImportError_(Exception):
    """A reason to show the person ("That video isn't a recipe")."""


def platform(url: str) -> str | None:
    """youtube, instagram or web, by the link's host (so yt-dlp only ever gets YouTube and Instagram links)."""
    if not re.match(r"https?://[^/\s]+\.[^/\s]+", url):
        return None
    try:
        host = (httpx.URL(url).host or "").lower()
    except httpx.InvalidURL:
        return None
    if host in ("youtu.be", "youtube.com") or host.endswith(".youtube.com"):
        return "youtube"
    if (host == "instagram.com" or host.endswith(".instagram.com")) and re.search(r"instagram\.com/(?:[\w.]+/)?(reel|reels|p|tv)/", url):
        return "instagram"
    return "web"


def public_host(url: httpx.URL) -> bool:
    """True if every address the host resolves to is on the public internet. Links people paste are
    fetched from the server, which sits on a home network: never let them reach the router, the
    other services on the box, or the cloud metadata address."""
    if url.scheme not in ("http", "https") or not url.host:
        return False
    try:
        infos = socket.getaddrinfo(url.host, url.port or (443 if url.scheme == "https" else 80), type=socket.SOCK_STREAM)
    except OSError:
        return False
    return bool(infos) and all(ipaddress.ip_address(info[4][0].split("%")[0]).is_global for info in infos)


def safe_get(url: str, *, timeout: float, headers: dict, max_bytes: int = FILE_MAX) -> httpx.Response:
    """GET a public web address, following up to 5 redirects and checking each hop with public_host."""
    target = httpx.URL(url)
    with httpx.Client(timeout=timeout, headers=headers, follow_redirects=False) as http:
        for _ in range(6):
            if not public_host(target):
                raise ImportError_("That link points somewhere the server won't go.")
            with http.stream("GET", target) as r:
                if r.is_redirect and "location" in r.headers:
                    target = target.join(r.headers["location"])
                    continue
                body = bytearray()
                for chunk in r.iter_bytes():
                    body += chunk
                    if len(body) > max_bytes:
                        raise ImportError_("That page is too big to read.")
                # The body is already decompressed: drop the headers that describe it on the wire.
                kept = [(k, v) for k, v in r.headers.multi_items() if k.lower() not in ("content-encoding", "content-length", "transfer-encoding")]
                return httpx.Response(r.status_code, headers=kept, content=bytes(body), request=r.request)
    raise ImportError_("That link redirects too many times.")


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
    # Web pages: drop the fragment and tracking parameters (utm_*, fbclid…).
    url = url.split("#")[0]
    if "?" in url:
        base, query = url.split("?", 1)
        keep = [p for p in query.split("&") if p and not re.match(r"(utm_\w+|fbclid|gclid|mc_\w+|ref|igshid)=", p)]
        url = base + ("?" + "&".join(keep) if keep else "")
    return url


def run(job_id: int) -> None:
    """Background worker: import one job, recording progress on it as it goes."""
    with _one_at_a_time, Session(engine) as session:
        job = session.get(ImportJob, job_id)
        if job is None:  # its account was deleted while it waited
            return

        def step(status: str, message: str = "") -> None:
            job.status, job.message = status, message
            session.add(job)
            session.commit()

        try:
            recipe = import_file(session, job, step) if job.file else \
                import_page(session, job, step) if platform(job.url) == "web" else import_video(session, job, step)
            job.recipe_id = recipe.id
            step("done", recipe.title)
        except ImportError_ as e:
            step("failed", str(e))
        except Exception as e:  # noqa: BLE001 — anything else: keep the worker alive, say what broke
            log.exception("import %s failed", job.url)
            step("failed", f"Something went wrong: {e}")
        finally:
            if job.file:
                (UPLOAD_DIR / job.file).unlink(missing_ok=True)


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
    return save_import(session, job, info, data, kind)


def import_page(session: Session, job: ImportJob, step) -> Recipe:
    if not GEMINI_KEY:
        raise ImportError_("Importing isn't set up on the server yet (no Gemini key).")
    step("fetching", "Reading the page")
    try:
        r = fetch(job.url)
    except Blocked:
        # The site turns the server away (Cloudflare and the like): let Gemini fetch the page itself.
        step("reading", "Writing the recipe")
        prompt = PAGE_PROMPT.format(rules=RULES, title="", site="", url=job.url, jsonld="(read the page)",
                                    text="(read the page at the link above)")
        data = ask_gemini([{"text": prompt}], "the page", tools=[{"url_context": {}}])
        if not data.get("is_recipe") or not data.get("ingredients"):
            raise ImportError_("That site won't let the server read it. Save the page as a PDF (Print → Save as PDF) and import the file.")
        step("saving", "Working out calories")
        return save_import(session, job, {"uploader": re.sub(r"^www\.", "", httpx.URL(job.url).host)}, data, "web")
    if "pdf" in r.headers.get("content-type", "") or r.content[:5] == b"%PDF-":
        return import_document(session, job, step, r.content, "application/pdf", job.url)
    page = read_page(r.text, str(r.url))

    step("reading", "Writing the recipe")
    prompt = PAGE_PROMPT.format(rules=RULES, title=page["title"], site=page["site"], url=job.url,
                                jsonld=json.dumps(page["recipe"], ensure_ascii=False)[:20000] if page["recipe"] else "(none)",
                                text=page["text"][:(20000 if page["recipe"] else 60000)] or "(none)")
    data = ask_gemini([{"text": prompt}], "the page")
    if not data.get("is_recipe") or not data.get("ingredients"):
        raise ImportError_("I couldn't find a recipe on that page.")

    step("saving", "Working out calories")
    info = {"title": page["title"], "uploader": page["author"] or page["site"], "thumbnail": page["image"]}
    return save_import(session, job, info, data, "web")


def import_file(session: Session, job: ImportJob, step) -> Recipe:
    path = UPLOAD_DIR / job.file
    if path.suffix[1:] in TEXT_EXTS:
        return import_text(session, job, step, path)
    mime = next((t for t, ext in FILE_TYPES.items() if path.suffix == f".{ext}"), "application/pdf")
    return import_document(session, job, step, path.read_bytes(), mime, None)


def import_document(session: Session, job: ImportJob, step, data: bytes, mime: str, url: str | None) -> Recipe:
    """A PDF or a photo, uploaded or linked to."""
    if not GEMINI_KEY:
        raise ImportError_("Importing isn't set up on the server yet (no Gemini key).")
    if len(data) > FILE_MAX:
        raise ImportError_("That file is too big to import (40 MB at most).")
    what = "PDF" if mime == "application/pdf" else "photo"
    step("reading", f"Reading the {what} and writing the recipe")
    with httpx.Client(timeout=httpx.Timeout(300, connect=20), headers={"x-goog-api-key": GEMINI_KEY}) as http:
        if len(data) <= INLINE_MAX:
            part = {"inline_data": {"mime_type": mime, "data": base64.b64encode(data).decode()}}
        else:
            with tempfile.NamedTemporaryFile(suffix="." + FILE_TYPES.get(mime, "bin")) as f:
                f.write(data)
                f.flush()
                part = {"file_data": {"file_uri": upload(http, Path(f.name), mime), "mime_type": mime}}
    name = job.url if url is None else url.rsplit("/", 1)[-1]
    recipe = ask_gemini([part, {"text": DOC_PROMPT.format(rules=RULES, kind=what, name=name)}], f"the {what}")
    if not recipe.get("is_recipe") or not recipe.get("ingredients"):
        raise ImportError_(f"I couldn't find a recipe in that {what}.")

    step("saving", "Working out calories")
    return save_import(session, job, {"title": Path(name).stem}, recipe, "pdf" if what == "PDF" else "photo")


def import_text(session: Session, job: ImportJob, step, path: Path) -> Recipe:
    """A YAML/JSON/text recipe file. Big exports can hold many recipes; this takes the first one."""
    if not GEMINI_KEY:
        raise ImportError_("Importing isn't set up on the server yet (no Gemini key).")
    text = path.read_bytes().decode("utf-8", errors="replace")[:TEXT_MAX]
    step("reading", "Reading the file and writing the recipe")
    prompt = DOC_PROMPT.format(rules=RULES, kind="file", name=job.url) + "\nFile contents:\n" + text
    data = ask_gemini([{"text": prompt}], "the file")
    if not data.get("is_recipe") or not data.get("ingredients"):
        raise ImportError_("I couldn't find a recipe in that file.")
    step("saving", "Working out calories")
    return save_import(session, job, {"title": Path(job.url).stem}, data, "file")


# --- web pages

class Blocked(Exception):
    """The site turned the server away."""


def fetch(url: str) -> httpx.Response:
    try:
        r = safe_get(url, headers=BROWSER, timeout=30)
    except httpx.HTTPError as e:
        raise ImportError_(f"Couldn't open that page ({type(e).__name__}).") from e
    if r.status_code in (401, 403, 429, 503):
        raise Blocked()
    if r.status_code == 404:
        raise ImportError_("That page doesn't exist (404).")
    if r.status_code >= 400:
        raise ImportError_(f"Couldn't open that page ({r.status_code}).")
    return r


def read_page(page: str, url: str) -> dict:
    """The recipe's schema.org data (if any), title, photo, author and readable text of a page."""
    from html.parser import HTMLParser

    class Reader(HTMLParser):
        SKIP = {"script", "style", "noscript", "svg", "template", "iframe", "nav", "footer", "form", "button"}
        BLOCK = {"p", "div", "li", "br", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "ul", "ol", "table"}

        def __init__(self):
            super().__init__(convert_charrefs=True)
            self.jsonld: list[str] = []
            self.meta: dict[str, str] = {}
            self.title = ""
            self.text: list[str] = []
            self._skip = 0
            self._in = None

        def handle_starttag(self, tag, attrs):
            a = dict(attrs)
            if tag == "script" and (a.get("type") or "").lower() == "application/ld+json":
                self._in, self.jsonld = "jsonld", self.jsonld + [""]
            elif tag == "title":
                self._in = "title"
            elif tag == "meta" and (key := a.get("property") or a.get("name")) and a.get("content"):
                self.meta.setdefault(key.lower(), a["content"])
            if tag in self.SKIP:
                self._skip += 1
            if tag in self.BLOCK:
                self.text.append("\n")

        def handle_endtag(self, tag):
            if tag in ("script", "title"):
                self._in = None
            if tag in self.SKIP and self._skip:
                self._skip -= 1
            if tag in self.BLOCK:
                self.text.append("\n")

        def handle_data(self, data):
            if self._in == "jsonld":
                self.jsonld[-1] += data
            elif self._in == "title":
                self.title += data
            elif not self._skip:
                self.text.append(data)

    reader = Reader()
    reader.feed(page)
    recipe = None
    for block in reader.jsonld:
        try:
            recipe = find_recipe(json.loads(block, strict=False))
        except json.JSONDecodeError:
            continue
        if recipe:
            break
    text = re.sub(r"[ \t\r\f\v]+", " ", "".join(reader.text))
    text = re.sub(r"\s*\n\s*", "\n", text).strip()
    meta = reader.meta
    image = (first_url((recipe or {}).get("image")) or meta.get("og:image") or meta.get("twitter:image") or "").strip()
    return {
        "recipe": recipe,
        "title": html.unescape((recipe or {}).get("name") or meta.get("og:title") or reader.title).strip(),
        "site": meta.get("og:site_name") or re.sub(r"^www\.", "", httpx.URL(url).host),
        "author": person((recipe or {}).get("author")) or meta.get("author"),
        "image": httpx.URL(url).join(image).__str__() if image else None,
        "text": text,
    }


def find_recipe(node) -> dict | None:
    """The first schema.org Recipe in a JSON-LD blob (it may be nested in an @graph or a list)."""
    if isinstance(node, list):
        return next((r for n in node if (r := find_recipe(n))), None)
    if not isinstance(node, dict):
        return None
    kind = node.get("@type")
    if kind == "Recipe" or (isinstance(kind, list) and "Recipe" in kind):
        return node
    return find_recipe(node.get("@graph") or node.get("mainEntity") or [])


def first_url(image) -> str | None:
    if isinstance(image, str):
        return image
    if isinstance(image, list):
        return next((u for i in image if (u := first_url(i))), None)
    if isinstance(image, dict):
        return image.get("url") or image.get("contentUrl")
    return None


def person(author) -> str | None:
    if isinstance(author, list):
        return person(author[0]) if author else None
    if isinstance(author, dict):
        return author.get("name")
    return author if isinstance(author, str) else None


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

# The parts of the rules every kind of import shares.
RULES = """- Group ingredients into sections when the recipe has parts ("Marinade", "Sauce", "To serve").
- Steps: short, clear instructions in order, each with an optional short title.
- servings: how many portions it makes (a number). total_minutes: the total time if said or clear.
- category: one of Breakfast, Lunch, Dinner, Side, Snack, Dessert, Drink. cuisine: e.g. Indian, Mexican.
- tags: pick any that fit from: Easy, Level Up, Chicken, Beef, Pork, Eggs, Seafood, Vegetarian, Stir Fry,
  Bake, Braise, Sear, Crispy, Grill, High Protein, Gluten Free, Low Fat, Low Carb, Dairy Free, Meal Prep.
  (Not time: that's total_minutes.)
"""

PROMPT = """You turn cooking videos into recipes for a recipe app.

Watch the video (listen to what's said and read any text on screen) and read the video's
description below. Write the recipe that's cooked in it.

Rules:
- The description often has the exact quantities, ingredient list, servings and macros. When the
  description and the video disagree, trust the description.
- Write each ingredient's amount the way a recipe would ("200 g", "2 tbsp", "3 cloves", "1 can",
  "a drizzle", "to taste"). Keep the creator's units.
- Every ingredient needs an amount, so the app can count calories. When the creator doesn't give
  quantities (or servings), write the recipe for 2 servings: set servings to 2 and give each
  ingredient a sensible amount for 2 portions, judging from what you see in the video (e.g. "300 g",
  "1 tbsp", "2 cloves"). Set amounts_estimated to true when you did this for any ingredient. Only
  seasoning to taste (salt, pepper) or garnish may be "to taste".
{rules}- stated_nutrition: ONLY numbers the creator actually gives (in the description, on screen or said).
  Never estimate them yourself. Say whether they are per serving or for the whole recipe.
- If the video isn't a recipe (a vlog, a review…), set is_recipe to false.
- Write in English.

Video title: {title}
Creator: {creator}

Description:
{description}
"""

PAGE_PROMPT = """You turn recipe web pages into recipes for a recipe app.

Below is a page from a recipe site: the recipe data the site publishes for search engines
(schema.org JSON-LD) when it has one, and the page's text. Write the recipe on the page.

Rules:
- Take the recipe from the structured data and the recipe card. Ignore the story before it, ads,
  comments, and other recipes the page links to.
- Keep each ingredient's amount as the page writes it ("200 g", "2 tbsp", "3 cloves", "1 can",
  "to taste"). When it gives both US and metric amounts ("1 cup (240 ml)"), use the metric one.
- When an amount is missing, give a sensible one for the recipe's servings and set
  amounts_estimated to true. Only seasoning to taste or garnish may be "to taste". If servings
  aren't given, judge them from the amounts.
{rules}- stated_nutrition: ONLY numbers the page gives (the structured data's nutrition, or a nutrition
  box). Never estimate them yourself. Say whether they are per serving or for the whole recipe.
- If the page has no recipe on it (an article, a shop, a list of links…), set is_recipe to false.
- Write in English.

Page: {url}
Title: {title}
Site: {site}

Structured data:
{jsonld}

Page text:
{text}
"""

DOC_PROMPT = """You turn recipe documents into recipes for a recipe app.

The attached {kind} is a recipe: a printed or saved recipe, a cookbook page, a recipe card, a
handwritten note, a screenshot, or a recipe exported from another app (YAML, JSON, text). Read it and write the recipe in it. If it holds several
recipes, write the first complete one.

Rules:
- Keep each ingredient's amount as written ("200 g", "2 tbsp", "3 cloves", "1 can", "to taste").
  When it gives both US and metric amounts, use the metric one.
- When an amount is missing, give a sensible one for the recipe's servings and set
  amounts_estimated to true. Only seasoning to taste or garnish may be "to taste". If servings
  aren't given, judge them from the amounts.
- A recipe split across pages or columns is still one recipe; follow it to the end.
{rules}- stated_nutrition: ONLY numbers the {kind} gives. Never estimate them yourself. Say whether they are
  per serving or for the whole recipe.
- If there's no recipe in it, set is_recipe to false.
- Write in English.

File name: {name}
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "is_recipe": {"type": "boolean"},
        "title": {"type": "string", "description": "A short, clear recipe name (not the video's clickbait title)."},
        "description": {"type": "string", "description": "One or two sentences about the dish."},
        "servings": {"type": ["number", "null"]},
        "amounts_estimated": {"type": "boolean", "description": "True if you estimated any amounts (or the servings) yourself."},
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
                    "food": {"type": "string", "description": "Plain grocery name for a nutrition database, no brand: "
                             "'nonfat evaporated milk', 'light spreadable cheese', 'tomato puree', 'chicken thigh'."},
                    "grams": {"type": ["number", "null"], "description": "Your best estimate of the weight in grams of the amount "
                              "used (e.g. 1 wedge of spreadable cheese = 17). Null for water or 'to taste'."},
                },
                "required": ["name", "amount", "note", "food", "grams"],
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
    prompt = PROMPT.format(rules=RULES, title=info.get("title") or "", creator=info.get("uploader") or info.get("channel") or "",
                           description=(info.get("description") or "(none)")[:6000])
    with httpx.Client(timeout=httpx.Timeout(300, connect=20), headers={"x-goog-api-key": GEMINI_KEY}) as http:
        if kind == "youtube":
            video = {"file_data": {"file_uri": info.get("webpage_url") or url}}
        elif media.stat().st_size <= INLINE_MAX:
            video = {"inline_data": {"mime_type": "video/mp4", "data": base64.b64encode(media.read_bytes()).decode()}}
        else:
            video = {"file_data": {"file_uri": upload(http, media, "video/mp4"), "mime_type": "video/mp4"}}
    return ask_gemini([video, {"text": prompt}], "the video",
                      # a long video at full detail would use a lot of the free quota
                      mediaResolution="MEDIA_RESOLUTION_LOW")


def ask_gemini(parts: list[dict], what: str, tools: list | None = None, **config) -> dict:
    """Ask for the recipe in SCHEMA's shape; falls back to the lighter model when rate-limited."""
    body = {
        "contents": [{"role": "user", "parts": parts}],
        "generationConfig": {"responseMimeType": "application/json", "responseJsonSchema": SCHEMA, "temperature": 0.2} | config,
    } | ({"tools": tools} if tools else {})
    with httpx.Client(timeout=httpx.Timeout(300, connect=20), headers={"x-goog-api-key": GEMINI_KEY}) as http:
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
    raise ImportError_(f"Gemini couldn't read {what} ({detail[:160]}).")


def upload(http: httpx.Client, path: Path, mime: str) -> str:
    """Files API: resumable upload, then wait until the file is ready."""
    size = path.stat().st_size
    start = http.post(f"{GEMINI}/upload/v1beta/files", json={"file": {"display_name": path.name}}, headers={
        "X-Goog-Upload-Protocol": "resumable", "X-Goog-Upload-Command": "start",
        "X-Goog-Upload-Header-Content-Length": str(size), "X-Goog-Upload-Header-Content-Type": mime})
    start.raise_for_status()
    done = http.post(start.headers["x-goog-upload-url"], content=path.read_bytes(), headers={
        "Content-Length": str(size), "X-Goog-Upload-Offset": "0", "X-Goog-Upload-Command": "upload, finalize"})
    done.raise_for_status()
    f = done.json()["file"]
    for _ in range(60):
        if f.get("state") == "ACTIVE":
            return f["uri"]
        if f.get("state") == "FAILED":
            raise ImportError_("Gemini couldn't process that file.")
        time.sleep(2)
        f = http.get(f"{GEMINI}/v1beta/{f['name']}").json()
    raise ImportError_("Gemini took too long to process the file.")


def parse(resp: dict) -> dict:
    try:
        text = "".join(p.get("text", "") for p in resp["candidates"][0]["content"]["parts"])
        return json.loads(text)
    except (KeyError, IndexError, json.JSONDecodeError) as e:
        reason = (resp.get("candidates") or [{}])[0].get("finishReason") or resp.get("promptFeedback", {}).get("blockReason")
        raise ImportError_(f"Gemini's answer wasn't a recipe ({reason or e}).") from e


# --- saving

def save_import(session: Session, job: ImportJob, info: dict, data: dict, kind: str) -> Recipe:
    """kind: youtube | instagram | web | pdf | photo | file (the recipe's source)."""
    video = kind in ("youtube", "instagram")
    where = "the video" if video else "the page" if kind == "web" else f"the {kind if kind != 'pdf' else 'PDF'}"
    stated = data.get("stated_nutrition") or {}
    source_nutrition = None
    if any(stated.get(k) for k in ("calories", "protein", "carbohydrates", "fat")):
        source_nutrition = {k: stated.get(k) for k in ("calories", "protein", "carbohydrates", "fat") if stated.get(k) is not None}
        source_nutrition |= {"per": stated.get("per") or "serving", "from": "creator"}
    # Amounts the source never gave: Gemini estimates them (for 2 servings in a video, see PROMPT); where it
    # still left one blank, its weight guess stands in, so every ingredient counts.
    servings = data.get("servings") or 2
    estimated = bool(data.get("amounts_estimated")) or not data.get("servings")
    for i in data["ingredients"]:
        if not (i.get("amount") or "").strip() and i.get("grams"):
            i["amount"] = f"{round(i['grams'])} g"
            estimated = True
    body = RecipeIn(
        title=(data.get("title") or info.get("title") or "Imported recipe")[:120],
        description=data.get("description") or "",
        image_url=keep_thumbnail(info),
        video_url=job.url if video else None,
        source_url=None if job.file else job.url,
        servings=servings,
        notes=f"Amounts estimated for 2 servings: {where} doesn't give them all." if estimated and servings == 2 and video
              else f"Some amounts are estimates: {where} doesn't give them all." if estimated else "",
        yield_text=data.get("yield_text") or None,
        total_minutes=data.get("total_minutes") or None,
        cuisine=data.get("cuisine") or None,
        category=data.get("category") or None,
        tags=[t for t in data.get("tags") or [] if t][:8],
        ingredients=[IngredientIn(group=i.get("section") or None, name=i["name"], note=i.get("note") or "", label=i.get("amount") or "",
                                  food_hint=i.get("food") or None, grams_hint=i.get("grams") or None)
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
    """Save the video's cover photo (Instagram's links expire) or the page's photo as the recipe's photo."""
    url = info.get("thumbnail")
    if not url:
        return None
    try:
        r = safe_get(url, timeout=20, headers=BROWSER | {"Accept": "image/*"}, max_bytes=15 * 1024 * 1024)
        r.raise_for_status()
        if not r.headers.get("content-type", "").startswith("image/"):
            return None
        ext = {"image/png": "png", "image/webp": "webp"}.get(r.headers.get("content-type", "").split(";")[0], "jpg")
        IMAGE_DIR.mkdir(parents=True, exist_ok=True)
        name = f"{os.urandom(12).hex()}.{ext}"
        (IMAGE_DIR / name).write_bytes(r.content)
        return f"/api/images/{name}"
    except Exception:  # noqa: BLE001 — a recipe without a photo is fine
        return url if "ytimg" in url else None


def find_existing(session: Session, owner_id: int, url: str) -> Recipe | None:
    return session.exec(select(Recipe).where(Recipe.owner_id == owner_id, Recipe.source_url == url)).first()

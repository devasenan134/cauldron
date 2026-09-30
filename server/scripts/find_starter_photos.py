"""Find a freely licensed photo for each starter recipe on Wikimedia Commons.

Only licences that allow reuse, including commercial use, are taken: CC0, public domain, CC BY
and CC BY-SA (never NC or ND). Each pick is stored in app/starter/recipes.json with its credit
(author, licence, and the file's page), and linked as an 800 px Commons thumbnail.

A wrong dish is worse than no photo, so look before you keep them: the script writes a contact
sheet (data/starter-photos.html) with every pick, its title and description. Set a recipe's
"image" to null to drop its photo, or give it a "photo_query" and run this again with --only.

    uv run python scripts/find_starter_photos.py                # show what it would pick
    uv run python scripts/find_starter_photos.py --write        # save the picks to recipes.json
    uv run python scripts/find_starter_photos.py --only sambar --redo --write
    uv run python scripts/seed_starter.py --refresh             # then put them in the database
"""
import argparse
import html
import json
import re
import sys
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.db import DB_PATH  # noqa: E402
from app.starter import FILE, USER_AGENT  # noqa: E402

API = "https://commons.wikimedia.org/w/api.php"
# Words that say nothing about which dish it is.
STOP = {"with", "and", "the", "style", "quick", "easy", "classic", "my", "homemade", "lighter", "juicy", "crispy", "fluffy",
        "smooth", "chewy", "crunchy", "high", "protein", "meal", "prep", "cooked", "roasted", "baked", "spiced"}


def allowed(licence: str) -> bool:
    """CC0, public domain, CC BY or CC BY-SA, in any version."""
    short = licence.strip().lower()
    if re.search(r"\b(nc|nd)\b", short) or any(x in short for x in ("gfdl", "fair use", "non-commercial")):
        return False
    return short.startswith(("cc0", "public domain", "pd")) or re.match(r"cc[ -]by(-sa)?( \d|$)", short) is not None


def text(value: str) -> str:
    """Commons metadata is HTML; keep the words."""
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()


def words(s: str) -> set[str]:
    return {w for w in re.findall(r"[a-z]+", s.lower()) if len(w) > 2 and w not in STOP}


def candidates(http: httpx.Client, query: str) -> list[dict]:
    r = http.get(API, params={
        "action": "query", "format": "json", "generator": "search", "gsrsearch": f"{query} filetype:bitmap",
        "gsrnamespace": 6, "gsrlimit": 15, "prop": "imageinfo", "iiprop": "url|extmetadata|mime|size", "iiurlwidth": 800,
    })
    r.raise_for_status()
    pages = sorted((r.json().get("query") or {}).get("pages", {}).values(), key=lambda p: p.get("index", 0))
    out = []
    for p in pages:
        info = (p.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata") or {}
        licence = text((meta.get("LicenseShortName") or {}).get("value", ""))
        if info.get("mime") not in ("image/jpeg", "image/png", "image/webp") or not allowed(licence) or info.get("width", 0) < 600:
            continue
        out.append({
            "title": p["title"].removeprefix("File:"),
            "description": text((meta.get("ImageDescription") or {}).get("value", ""))[:300],
            "url": info.get("thumburl") or info["url"],
            "credit": {"author": text((meta.get("Artist") or {}).get("value", "")) or "Unknown",
                       "license": licence, "license_url": text((meta.get("LicenseUrl") or {}).get("value", "")) or None,
                       "source_url": info.get("descriptionurl")},
        })
    return out


def pick(recipe: dict, found: list[dict]) -> dict | None:
    """The first result that names the dish (in its file name or description)."""
    want = words(recipe.get("photo_query") or recipe["title"])
    for c in found:
        if want and len(want & words(c["title"] + " " + c["description"])) >= min(2, len(want)):
            return c
    return None


def contact_sheet(rows: list[tuple[dict, dict | None]]) -> str:
    cells = []
    for recipe, c in rows:
        if c is None:
            cells.append(f"<div class=c><b>{html.escape(recipe['title'])}</b><p>no photo</p></div>")
            continue
        cells.append(f"<div class=c><img src='{html.escape(c['url'])}' loading=lazy><b>{html.escape(recipe['title'])}</b>"
                     f"<p><code>{html.escape(recipe['slug'])}</code> · {html.escape(c['title'])}</p><p>{html.escape(c['description'])}</p>"
                     f"<p>{html.escape(c['credit']['author'])} · {html.escape(c['credit']['license'])} · "
                     f"<a href='{html.escape(c['credit']['source_url'] or '')}'>page</a></p></div>")
    return ("<!doctype html><meta charset=utf-8><title>Starter photos</title><style>body{font:14px sans-serif;margin:16px}"
            ".g{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}.c img{width:100%;aspect-ratio:1;object-fit:cover}"
            "p{margin:4px 0;color:#555}</style><h1>Starter recipe photos: check each one shows the right dish</h1><div class=g>"
            + "".join(cells) + "</div>")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", nargs="*", help="just these recipe slugs")
    parser.add_argument("--redo", action="store_true", help="look again for recipes that already have a photo")
    parser.add_argument("--write", action="store_true", help="save the picks to recipes.json")
    args = parser.parse_args()

    recipes = json.loads(FILE.read_text())
    rows = []
    with httpx.Client(headers={"User-Agent": USER_AGENT}, timeout=30) as http:
        for recipe in recipes:
            if args.only and recipe["slug"] not in args.only:
                continue
            if recipe.get("image") and not args.redo:
                rows.append((recipe, {"url": recipe["image"], "title": "(kept)", "description": "", "credit": recipe["image_credit"]}))
                continue
            chosen = pick(recipe, candidates(http, recipe.get("photo_query") or recipe["title"]))
            print(f"{recipe['slug']:32} {chosen['title'] + '  [' + chosen['credit']['license'] + ']' if chosen else '-'}")
            rows.append((recipe, chosen))
            if chosen:
                recipe["image"], recipe["image_credit"] = chosen["url"], chosen["credit"]
    sheet = DB_PATH.parent / "starter-photos.html"
    sheet.parent.mkdir(parents=True, exist_ok=True)
    sheet.write_text(contact_sheet(rows))
    print(f"\ncontact sheet: {sheet}")
    if args.write:
        FILE.write_text(json.dumps(recipes, ensure_ascii=False, indent=1) + "\n")
        print(f"saved {FILE}; now run scripts/seed_starter.py --refresh")


if __name__ == "__main__":
    main()

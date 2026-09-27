"""Download every recipe on cookwell.com into data/raw/cookwell/<slug>.json.

Recipe pages are Next.js server components: the full recipe (gram weights,
grocery aisles, components, macros) lives in the RSC flight payload, which is
much richer than the page's schema.org JSON-LD. Resumable: already-saved slugs
are skipped. Polite: one request per second.
"""
import json
import re
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.cookwell_flight import parse, resolve  # noqa: E402

BASE = "https://www.cookwell.com"
OUT = Path(__file__).resolve().parents[2] / "data" / "raw" / "cookwell"
UA = "Mozilla/5.0 (personal recipe archive)"


def recipe_slugs(client: httpx.Client) -> list[str]:
    xml = client.get(f"{BASE}/server-sitemap.xml").text
    return sorted(set(re.findall(r"<loc>https://www\.cookwell\.com/recipe/([^<]+)</loc>", xml)))


def flight_payload(html: str) -> str:
    chunks = re.findall(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)</script>', html, re.S)
    return "".join(json.loads(f'"{c}"') for c in chunks)


def find_recipe(v):
    if isinstance(v, dict):
        if "steps" in v and "nutrition" in v and "slug" in v:
            return v
        v = v.values()
    if isinstance(v, (list, type({}.values()))):
        for x in v:
            if (found := find_recipe(x)) is not None:
                return found
    return None


def extract(html: str) -> dict | None:
    recs = parse(flight_payload(html))
    for rec in recs.values():
        if (raw := find_recipe(rec)) is not None:
            return resolve(recs, raw)
    return None


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with httpx.Client(headers={"User-Agent": UA}, timeout=30, follow_redirects=True) as client:
        slugs = recipe_slugs(client)
        todo = [s for s in slugs if not (OUT / f"{s}.json").exists()]
        print(f"{len(slugs)} recipes, {len(todo)} to fetch")
        failed = []
        for i, slug in enumerate(todo, 1):
            try:
                resp = client.get(f"{BASE}/recipe/{slug}")
                resp.raise_for_status()
                recipe = extract(resp.text)
                if recipe is None:
                    raise ValueError("no recipe object in page")
                (OUT / f"{slug}.json").write_text(json.dumps(recipe, ensure_ascii=False, indent=1))
                print(f"[{i}/{len(todo)}] {slug}")
            except Exception as e:  # keep going; rerun picks up failures
                failed.append(slug)
                print(f"[{i}/{len(todo)}] FAILED {slug}: {e}")
            time.sleep(1)
        if failed:
            print(f"{len(failed)} failed: {failed}")


if __name__ == "__main__":
    main()

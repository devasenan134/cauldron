"""Add the starter recipes (app/starter/recipes.json) to the database.

The server does this by itself when it starts, adding only the ones that are missing. Run this
with --refresh after editing recipes.json or the food mapping: every starter recipe is written
again from the file and its foods are looked up afresh.

    uv run python scripts/seed_starter.py [--refresh]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sqlmodel import Session  # noqa: E402

from app import starter  # noqa: E402
from app.db import engine, init_db  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--refresh", action="store_true", help="write every starter recipe again from the file")
    args = parser.parse_args()
    init_db()
    with Session(engine) as session:
        print(f"wrote {starter.seed(session, refresh=args.refresh)} starter recipes")
        print(f"downloaded {starter.fetch_photos(session)} photos")


if __name__ == "__main__":
    main()

# Cauldron

A personal cooking app: a recipe library, a drag-and-drop meal planner that builds the
grocery list, calorie tracking per ingredient, dish and portion, and batch-cook
tracking (portions left). There's a website and an Android app, backed by one API
server on craftingtable.

## Layout

- `server/`: FastAPI + SQLite (SQLModel). Python 3.12+, managed with `uv`.
- `data/`: the local database and raw imports (gitignored).

## Run locally

```sh
cd server
uv run python scripts/fetch_cookwell.py    # download cookwell.com recipes (resumable, 1 req/s)
uv run python scripts/import_cookwell.py   # load them into data/cauldron.db (idempotent)
uv run uvicorn app.main:app --reload --port 8765
```

API docs: http://localhost:8765/docs

## Roadmap

1. ✅ Backend, database, Cook Well recipe import
2. Nutrition: USDA FoodData Central per ingredient → dish → serving
3. Website: library, planner (drag-and-drop), grocery list
4. Batch cooking: portions left, calories per portion
5. Import from YouTube / Reels / Shorts (yt-dlp + transcript + LLM)
6. Android app (+ share-sheet import)
7. Google sign-in and friends

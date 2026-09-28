# Cauldron

A personal cooking app: a recipe library, a drag-and-drop meal planner that builds the
grocery list, calorie tracking per ingredient, dish and portion, and batch-cook
tracking (portions left). There's a website and an Android app, backed by one API
server on craftingtable.

## Layout

- `server/`: FastAPI + SQLite (SQLModel). Python 3.12+, managed with `uv`.
- `web/`: the website: React + TypeScript (Vite, Tailwind, TanStack Query, dnd-kit).
- `android/`: the Android app: Kotlin + Jetpack Compose (package `io.github.devasenan134.cauldron`).
- `data/`: the local database and raw imports (gitignored).

## Run locally

```sh
cd server
uv run python scripts/fetch_cookwell.py    # download cookwell.com recipes (resumable, 1 req/s)
# USDA FoodData Central: put the Foundation + SR Legacy JSON zips from
# https://fdc.nal.usda.gov/download-datasets in data/usda/ and unzip them
uv run python scripts/load_usda.py         # load ~8k foods with nutrients per 100 g
uv run python scripts/import_cookwell.py   # load recipes (skips ones already there)
uv run uvicorn app.main:app --reload --port 8765

cd ../web
npm install
npm run dev        # http://localhost:5173 (proxies /api to :8765)
```

API docs: http://localhost:8765/docs. After `npm run build`, the API server also
serves the website at http://localhost:8765.

## Deploy (craftingtable)

`docker compose up -d --build` builds the website and server into one image,
listening on port 8130. The database lives in `./data/cauldron.db` next to the
compose file, so a backup is a copy of that file.

## Android app

```sh
cd android
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Builds are signed with the Cauldron key (`CAULDRON_KEYSTORE`, `CAULDRON_KEYSTORE_PASSWORD`,
`CAULDRON_KEY_ALIAS` in `~/.gradle/gradle.properties`), debug builds too: Google sign-in
only answers apps whose signing-key SHA-1 is registered as an Android OAuth client in the
same Google Cloud project as the website's client. The app sends its session as
`Authorization: Bearer`; the website uses a cookie.

Testing against a local server on the emulator, without Google:

```sh
./gradlew assembleDebug -PapiUrl=http://10.0.2.2:8765 -PdevToken=<a session token>
```

To release a new version, bump `versionCode` and `versionName` in
`android/app/build.gradle.kts`, then:

```sh
scripts/release-android.sh "- What's new"
```

It uploads the APK and notes to `data/apk/` on the server (the app checks
`/api/app/latest` and updates itself from there, since the GitHub repository is
private) and creates the GitHub release.

The grocery list works offline: changes apply at once and sync in order when the
server is reachable again.

## Nutrition

Each ingredient links to a food (`app/foodmap.py`, hand-checked for every
ingredient name in the Cook Well recipes) and gets a weight in grams. The weight
comes from the recipe, from "parts" in the same component, from a count times a
unit weight (1 clove, 2 slices), or from a typical amount for vague quantities
("a drizzle"). "To taste" and ingredients with no amount are left out and listed.
You can set any ingredient's food or grams by hand (`PATCH /ingredients/{id}`), and
hand edits are never overwritten.

## Roadmap

1. ✅ Backend, database, Cook Well recipe import
2. ✅ Nutrition: USDA FoodData Central per ingredient → dish → serving
3. ✅ Website: library, planner (drag-and-drop), grocery list
4. ✅ Batch cooking: portions left, calories per portion
5. Import from YouTube / Reels / Shorts (yt-dlp + transcript + LLM)
6. ✅ Android app (share-sheet import comes with step 5)
7. ✅ Google sign-in and friends

## License

Proprietary. Copyright (c) 2026 Devasenan Murugan. All rights reserved. See [LICENSE](LICENSE).

The Cook Well recipe library (text and photos © Ethan Chlebowski / Cook Well) is
imported for personal use only and must not ship in a commercial release.

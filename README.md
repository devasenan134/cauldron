# Cauldron

A cooking app that keeps the whole week in one place: your recipes, a drag-and-drop
meal planner that writes the grocery list, calories per ingredient, dish and portion,
and batch cooking (what's in the fridge, how many portions are left).

There's a website and an Android app, and it's open source: use the hosted one, or run
your own.

<a href="https://buymeacoffee.com/devaa"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-%E2%98%95-FFDD00?style=flat-square" alt="Buy me a coffee"></a>

## What it does

- **Recipes**: write your own, or import them from a YouTube video, an Instagram Reel,
  a recipe web page, a PDF or a photo (Gemini reads it and writes the recipe).
- **Calories and macros** for every ingredient, dish and portion, from USDA FoodData
  Central. Fix any weight or food by hand, or add your own foods.
- **Planner**: drag recipes onto days and meals; the grocery list builds itself, sorted
  by aisle, and works offline on your phone.
- **Batch cooking and prep**: cook once, eat for days. Leftovers go to the fridge and
  count down as you eat them; prepped ingredients (rice, sauces) are tracked by weight.
- **Meal log**: mark meals eaten or eaten out, and see calories against your daily goal.

## Use it

- **Website**: <https://cauldron.craftingtable.cc>. Sign in with Google.
- **Android**: download the APK from the website's Settings page or from
  [Releases](https://github.com/devasenan134/cauldron/releases). The app keeps itself up
  to date.

The hosted service runs on my own hardware at home, for free. If you find it useful, you
can [buy me a coffee](https://buymeacoffee.com/devaa). ☕

## Host it yourself

One Docker container and a free Google Cloud project. See **[SELF_HOSTING.md](SELF_HOSTING.md)**
for the server, sign-in, recipe imports and building the Android app for your server.

## Development

- `server/`: FastAPI + SQLite (SQLModel), Python 3.12+, managed with [uv](https://docs.astral.sh/uv/).
- `web/`: React + TypeScript (Vite, Tailwind, TanStack Query, dnd-kit).
- `android/`: Kotlin + Jetpack Compose.
- `data/`: the database, photos and downloads (not in git).

```sh
cd server
uv run python scripts/load_usda.py         # nutrition data: downloads USDA FoodData Central once
CAULDRON_OWNER_EMAIL=you@gmail.com CAULDRON_GOOGLE_CLIENT_ID=… \
    uv run uvicorn app.main:app --reload --port 8765

cd ../web
npm install
npm run dev        # http://localhost:5173 (proxies /api to :8765)
```

API docs are at http://localhost:8765/docs. After `npm run build`, the API server also
serves the website at http://localhost:8765. Every setting is listed in
[`.env.example`](.env.example).

### Android

```sh
cd android
./gradlew assembleDebug -PapiUrl=http://10.0.2.2:8765 -PdevToken=<a session token>
```

`-PdevToken` starts a debug build already signed in, which skips Google (handy on the
emulator; `10.0.2.2` is your computer as seen from the emulator). The app sends its
session as `Authorization: Bearer`; the website uses a cookie. For builds that sign in
with Google, see [SELF_HOSTING.md](SELF_HOSTING.md#5-the-android-app-optional).

### Nutrition

Each ingredient links to a food and gets a weight in grams. The weight comes from the
recipe, from "parts" in the same component, from a count times a unit weight (1 clove,
2 slices), or from a typical amount for vague quantities ("a drizzle"). "To taste" and
ingredients with no amount are left out and listed. Any ingredient's food or grams can be
set by hand, and hand edits are never overwritten.

## Contributing

Issues and pull requests are welcome. Contributions are accepted under the same licence
(Apache-2.0, section 5). To report a security problem, see [SECURITY.md](SECURITY.md).

## License

Copyright 2026 Devasenan Murugan. Licensed under the [Apache License 2.0](LICENSE): you can
use, change, host and sell it, as long as you keep the copyright and [NOTICE](NOTICE) and
say what you changed.

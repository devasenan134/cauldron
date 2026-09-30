# Hosting Cauldron yourself

Cauldron is one Docker container: an API server that also serves the website, with
everything stored in a `data/` folder (an SQLite database and photos). The Android app
talks to that server. This guide gets you:

1. the server and website running on your machine,
2. Google sign-in working for you (and whoever you invite),
3. optionally, recipe imports from videos and web pages,
4. optionally, the Android app, built for your server.

## What you need

- A computer that stays on, with [Docker](https://docs.docker.com/engine/install/) and
  Docker Compose (a home server, a Raspberry Pi 4/5, a cheap VPS; x86 or ARM).
- A web address with HTTPS, for example `cauldron.example.com`. Google sign-in only works
  on HTTPS sites (or `http://localhost` for trying it out). A
  [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
  or a reverse proxy like [Caddy](https://caddyserver.com/) gives you one; see
  [HTTPS](#https) below.
- A Google account, for a free Google Cloud project (sign-in only; there are no charges).

## 1. Get the code and settings

```sh
git clone https://github.com/devasenan134/cauldron.git
cd cauldron
cp .env.example .env
```

Open `.env` and set `CAULDRON_OWNER_EMAIL` to your Google account's email. The owner can
always sign in. You'll fill in `CAULDRON_GOOGLE_CLIENT_ID` in the next step.

## 2. Google sign-in

Cauldron has no passwords: people sign in with Google, and the server checks Google's
answer. That needs an OAuth client in a Google Cloud project of your own.

1. Go to <https://console.cloud.google.com/>, and create a project (any name, e.g. "Cauldron").
2. **APIs & Services → OAuth consent screen** (called **Google Auth Platform** in newer
   consoles): choose **External**, give the app a name, and enter your email as the support
   and developer contact. No extra scopes are needed: Cauldron only asks for the basic
   email and profile.
3. **Audience**: while the app is in **Testing**, only the accounts you add as *test
   users* can sign in (up to 100). That's fine for a household: add yourself and your
   family. To let anyone sign in, press **Publish app**. With only the basic scopes, Google
   doesn't need to review it.
4. **Credentials → Create credentials → OAuth client ID → Web application**. Under
   **Authorized JavaScript origins** add your site, e.g. `https://cauldron.example.com`
   (and `http://localhost:8130` and `http://localhost` if you want to try it locally). No
   redirect URIs are needed.
5. Copy the **Client ID** (`…apps.googleusercontent.com`) into `.env` as
   `CAULDRON_GOOGLE_CLIENT_ID`. The client ID isn't a secret; you never need the client
   secret.

## 3. Start it

```sh
docker compose up -d --build
```

The first build takes a few minutes. Cauldron listens on port **8130**: open
<http://localhost:8130> on the same machine.

Then load the nutrition data (USDA FoodData Central, about 8,000 foods; it downloads
14 MB once, which unpacks to about 200 MB in `data/usda/`, safe to delete afterwards):

```sh
docker compose exec cauldron uv run --no-sync python scripts/load_usda.py
```

Without it everything works, but recipes have no calories.

Sign in with the owner account. Your recipe book starts empty: write recipes, or import
them (next step).

### Who can sign in

In `.env`:

- `CAULDRON_SIGNUP=invite` (the default): only the owner and the emails in
  `CAULDRON_ALLOWED_EMAILS` (comma-separated).
- `CAULDRON_SIGNUP=open`: any Google account. Everyone but the owner gets daily limits on
  imports and photo uploads (`CAULDRON_IMPORTS_PER_DAY`, `CAULDRON_UPLOADS_PER_DAY`), and you
  can shut accounts out with `CAULDRON_BLOCKED_EMAILS`.

Run `docker compose up -d` after changing `.env`.

## 4. Recipe imports (optional)

Cauldron can turn a YouTube video, an Instagram Reel, a recipe web page, a PDF or a photo
into a recipe with Google's Gemini. Get a free API key at
<https://aistudio.google.com/apikey> and put it in `.env` as `CAULDRON_GEMINI_KEY`. The
free tier is plenty for a household. Imports run one at a time.

Instagram often asks for a login before it shows a Reel. If Reels fail, export your
instagram.com cookies in Netscape format (a "cookies.txt" browser extension does this) to
`data/cookies.txt`.

## HTTPS

Put Cauldron behind something that serves HTTPS and forwards to `localhost:8130`.

**Cloudflare Tunnel** (no open ports on your router): install `cloudflared`, create a
tunnel in the Zero Trust dashboard, and add a public hostname, such as
`cauldron.example.com`, pointing to `http://localhost:8130`.

**Caddy** (you have a domain pointing at your machine, with ports 80 and 443 open):

```
cauldron.example.com {
    reverse_proxy localhost:8130
}
```

Remember to add the final `https://` address to the OAuth client's JavaScript origins.

## 5. The Android app (optional)

The app in the releases here is built for the hosted Cauldron: it's tied to that server
and to its Google project, so it can't sign in to yours. Build your own copy instead. It's
the same app, pointed at your server.

You need JDK 17 and the Android SDK. [Android Studio](https://developer.android.com/studio)
installs both.

1. **A signing key.** Android only installs updates signed with the same key, so make one
   and keep it safe:

   ```sh
   keytool -genkeypair -v -keystore ~/cauldron-release.jks -alias cauldron \
       -keyalg RSA -keysize 4096 -validity 10000
   ```

   Then tell Gradle where it is, in `~/.gradle/gradle.properties` (not in the repository):

   ```properties
   CAULDRON_KEYSTORE=/home/you/cauldron-release.jks
   CAULDRON_KEYSTORE_PASSWORD=the password you chose
   CAULDRON_KEY_ALIAS=cauldron
   ```

   Without this, builds are signed with your computer's Android debug key. That works,
   but it's lost if you move computers.

2. **Pick a package name** of your own, like `com.example.cauldron`, so your app can sit
   next to the official one.

3. **An Android OAuth client.** Get your key's SHA-1:

   ```sh
   keytool -list -v -keystore ~/cauldron-release.jks -alias cauldron | grep SHA1
   ```

   In the same Google Cloud project as step 2: **Credentials → Create credentials → OAuth
   client ID → Android**, with your package name and that SHA-1. You don't need to copy
   anything from it. Google just needs to know the app is yours.

4. **Build**, with your server's address, your **web** client ID from step 2 (not the
   Android one), and your package name:

   ```sh
   cd android
   ./gradlew assembleRelease \
       -PapiUrl=https://cauldron.example.com \
       -PgoogleClientId=1234-abc.apps.googleusercontent.com \
       -PappId=com.example.cauldron
   ```

   The APK is `android/app/build/outputs/apk/release/app-release.apk`.

5. **Hand it out through your server.** Copy it to `data/apk/cauldron-<version>.apk`,
   using the `versionName` from `android/app/build.gradle.kts`, e.g. `cauldron-0.11.0.apk`.
   You can add release notes as `data/apk/cauldron-0.11.0.md`. Your website's **Settings**
   page then offers it for download, and installed apps update themselves from there when
   you add a newer version.

## Backups

Everything lives in `data/`: `cauldron.db` (all recipes, plans and accounts) and
`images/` (photos). To back up while Cauldron is running:

```sh
docker compose exec cauldron python -c "import sqlite3; sqlite3.connect('/data/cauldron.db').backup(sqlite3.connect('/data/backup.db'))"
```

Then copy `data/backup.db` and `data/images/` somewhere safe.

## Updating

```sh
git pull
docker compose up -d --build
```

The database updates itself when the server starts. Back up first anyway.

## Settings reference

Every setting is in [`.env.example`](.env.example), with a comment on what it does.

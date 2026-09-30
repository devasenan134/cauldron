# Security

## Reporting a problem

If you find a security problem in Cauldron, please don't open a public issue. Report it
privately through GitHub instead: the repository's **Security** tab → **Report a
vulnerability**. I'll reply as soon as I can and credit you in the fix if you'd like.

Please include what you found, how to reproduce it, and what someone could do with it.

## What's in scope

- The server (`server/`): sign-in, sessions, access between users' data, file uploads,
  and recipe imports (the server fetches links people paste).
- The website (`web/`) and the Android app (`android/`).

The hosted service at https://cauldron.craftingtable.cc runs this code. Please test
against your own copy (see [SELF_HOSTING.md](SELF_HOSTING.md)), not the hosted one, and
never access other people's data.

## Supported versions

Only the latest release gets fixes.

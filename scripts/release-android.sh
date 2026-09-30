#!/usr/bin/env bash
# Build the Android app and publish it: to the Cauldron server (where the app checks for
# updates) and as a GitHub release. The version comes from android/app/build.gradle.kts.
# This is how the hosted Cauldron is released; self-hosters, see SELF_HOSTING.md.
#
#   scripts/release-android.sh "What's new, in a line or a few bullet points"
#
# CAULDRON_SSH (default craftingtable) and CAULDRON_APK_DIR pick the server and its apk folder.
set -euo pipefail
cd "$(dirname "$0")/.."

notes="${1:?usage: $0 \"release notes\"}"
version=$(grep -oP 'versionName = "\K[^"]+' android/app/build.gradle.kts)
apk="cauldron-$version.apk"
host=${CAULDRON_SSH:-craftingtable}
server_dir=${CAULDRON_APK_DIR:-/mnt/ugreen/cauldron/data/apk}

if ssh "$host" "test -e $server_dir/$apk"; then
    echo "$apk is already on the server; bump versionName/versionCode first." >&2
    exit 1
fi

(cd android && ./gradlew assembleRelease -q)
tmp=$(mktemp -d)
cp android/app/build/outputs/apk/release/app-release.apk "$tmp/$apk"
printf '%s\n' "$notes" > "$tmp/cauldron-$version.md"

ssh "$host" "mkdir -p $server_dir"
ssh "$host" "cat > $server_dir/$apk.part && mv $server_dir/$apk.part $server_dir/$apk" < "$tmp/$apk"
ssh "$host" "cat > $server_dir/cauldron-$version.md" < "$tmp/cauldron-$version.md"
gh release create "v$version" "$tmp/$apk" --title "Cauldron $version" --notes "$notes"
cp "$tmp/$apk" /mnt/c/Users/devas/Downloads/ 2>/dev/null || true
echo "Published $version"

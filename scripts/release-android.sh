#!/usr/bin/env bash
# Build the Android app and publish it: to the Cauldron server (where the app checks for
# updates) and as a GitHub release. The version comes from android/app/build.gradle.kts.
#
#   scripts/release-android.sh "What's new, in a line or a few bullet points"
set -euo pipefail
cd "$(dirname "$0")/.."

notes="${1:?usage: $0 \"release notes\"}"
version=$(grep -oP 'versionName = "\K[^"]+' android/app/build.gradle.kts)
apk="cauldron-$version.apk"
server_dir=/mnt/ugreen/cauldron/data/apk

if ssh craftingtable "test -e $server_dir/$apk"; then
    echo "$apk is already on the server; bump versionName/versionCode first." >&2
    exit 1
fi

(cd android && ./gradlew assembleRelease -q)
tmp=$(mktemp -d)
cp android/app/build/outputs/apk/release/app-release.apk "$tmp/$apk"
printf '%s\n' "$notes" > "$tmp/cauldron-$version.md"

ssh craftingtable "mkdir -p $server_dir"
ssh craftingtable "cat > $server_dir/$apk.part && mv $server_dir/$apk.part $server_dir/$apk" < "$tmp/$apk"
ssh craftingtable "cat > $server_dir/cauldron-$version.md" < "$tmp/cauldron-$version.md"
gh release create "v$version" "$tmp/$apk" --title "Cauldron $version" --notes "$notes"
cp "$tmp/$apk" /mnt/c/Users/devas/Downloads/ 2>/dev/null || true
echo "Published $version"

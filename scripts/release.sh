#!/usr/bin/env bash
# Ship a build to GitHub Releases, where every phone looks for its update.
#
#   scripts/release.sh v2.0.0 notes.md
#
# Builds the signed release APK, writes version.json (the file the phone reads), tags the
# commit and creates the release with both files attached. Run from a clean, pushed tree.
# The release NOTES file is markdown for the GitHub page; the phone shows it as plain text.
#
# Needs: gh (signed in), JAVA_HOME pointing at Android Studio's jbr, local.properties with
# the keystore. versionCode in app/build.gradle.kts must already be bumped — it is what the
# phone compares, so it must only ever go up.
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:?tag, e.g. v2.0.0}"
NOTES="${2:?release notes file}"
REPO="Sacchukulal/MB-android"
VERSION="${TAG#v}"
CODE=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
NAME=$(sed -n 's/^ *versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)

[ "$NAME" = "$VERSION" ] || { echo "versionName is $NAME but the tag says $VERSION" >&2; exit 1; }
[ -n "$CODE" ] || { echo "no versionCode in app/build.gradle.kts" >&2; exit 1; }
[ -f "$NOTES" ] || { echo "no notes at $NOTES" >&2; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "commit first: the tree is not clean" >&2; exit 1; }

echo "== building $TAG (code $CODE)"
./gradlew :app:assembleRelease --console=plain -q

OUT="build/release"
mkdir -p "$OUT"
cp app/build/outputs/apk/release/app-release.apk "$OUT/magic-bill.apk"
SIZE=$(stat -c %s "$OUT/magic-bill.apk")

# The phone shows the notes as plain text: drop markdown marks, keep the lines.
PLAIN=$(sed -e 's/^#\+ *//' -e 's/\*\*//g' -e 's/^[-*] /• /' "$NOTES" | awk 'BEGIN{ORS="\\n"} {gsub(/\\/,"\\\\"); gsub(/"/,"\\\""); print}')

cat > "$OUT/version.json" <<EOF
{
  "version": "$VERSION",
  "version_code": $CODE,
  "apk_url": "https://github.com/$REPO/releases/latest/download/magic-bill.apk",
  "apk_size": $SIZE,
  "published": "$(date -u +%Y-%m-%d)",
  "release_notes": "$PLAIN"
}
EOF

echo "== releasing $TAG"
gh release create "$TAG" "$OUT/magic-bill.apk" "$OUT/version.json" \
  --repo "$REPO" --title "Magic Bill $TAG" --notes-file "$NOTES" --latest
echo "https://github.com/$REPO/releases/tag/$TAG"

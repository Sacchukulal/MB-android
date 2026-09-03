# Magic Bill — Android

Native Android companion app for [Magic Bill](https://magicbill.in) restaurant
billing. Kotlin + Jetpack Compose (Material 3), rebuilt from scratch in v2.0.0
(previously React Native).

## What it does

- **Owners** sign in with their Magic Bill account: live dashboard (today's
  revenue, payment split, 14-day trend, top items, peak-hour insights),
  date-range reports with PDF/CSV export, bill receipts, staff management
  with per-person permissions, subscription management.
- **Staff** sign in with a restaurant code + 4-digit PIN. What they see is
  exactly what the owner enables — down to hiding revenue amounts.
- **Offline-first**: every screen renders instantly from the on-device cache
  and refreshes silently. No spinners over data, no offline flashes.
- **Self-updating**: checks GitHub Releases and updates in place — no Play
  Store required.

## Download

Latest APK: <https://github.com/Sacchukulal/MB-android/releases/latest/download/magic-bill.apk>

## Building

```
# local.properties (gitignored)
sdk.dir=<android sdk path>
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
# optional, for release signing:
MB_KEYSTORE_FILE=keys/magic-bill-release.keystore
MB_KEYSTORE_PASSWORD=...
MB_KEY_ALIAS=magicbill
MB_KEY_PASSWORD=...

.\gradlew.bat :app:assembleDebug     # debug
.\gradlew.bat :app:assembleRelease   # signed, minified
```

Toolchain: Gradle 9.4.1 · AGP 9.2 (built-in Kotlin 2.3) · minSdk 26 ·
target/compile SDK 36/37.

## Releasing

Every phone looks at `releases/latest/download/version.json` once per start
(and from More → App update) and compares `version_code` with its own — so
`versionCode` in `app/build.gradle.kts` must go up on every release, and the
release's name can be anything. Old builds without a code compare by name.

```
# bump versionCode + versionName, commit, push, then:
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" scripts/release.sh v2.0.0 notes.md
```

The script builds the signed APK, writes `version.json`
(`version`, `version_code`, `apk_url`, `apk_size`, `published`,
`release_notes`), tags the commit and creates the GitHub release with both
files attached. CI does the same on a `v*` tag when signing secrets are
configured; without them it stays out of the way.

# Magic Bill Android — Kotlin Rebuild Report (v2.0.0)

**Date:** 17 July 2026
**Release:** https://github.com/Sacchukulal/MB-android/releases/tag/v2.0.0
**APK (permalink used by website QR / POS / in-app updater):**
https://github.com/Sacchukulal/MB-android/releases/latest/download/magic-bill.apk

## What happened

The React Native (Expo) app was replaced end-to-end by a native
**Kotlin + Jetpack Compose** app. Full RN source is preserved locally at
`MB-android-rn-backup\` (not pushed) and in git history before the rebuild
commits. The backend (Supabase ref `rlvygwituwywofwcwjsf`) needed **zero
changes** — every Edge Function contract (`staff-login`, `staff-data`,
`staff-manage`, `mobile-device`) and RLS read is consumed exactly as before.

## Headline numbers

| | RN v1.0.2 | Kotlin v2.0.0 |
|---|---|---|
| Release APK | 38.7 MB | **3.9 MB** |
| versionCode | 3 | 4 (in-place update works) |
| Signing cert | `8318c405…875f` | **same cert, verified** |

## Design — rebuilt, not copied

The "box inside box" card UI is gone. The new design language ("open
canvas"): content sits directly on the dark-navy canvas with emerald/teal
radial glows; sections separate with overline headers and whitespace;
lists are borderless rows; navigation is a floating pill bar with springy
selection; primary actions wear an emerald→teal gradient with press-squish.
Motion is standardized in `ui/theme/Motion.kt` (nav slides, tab drifts,
counter count-ups, staggered chart growth, shimmer skeletons, haptic ticks).

## Feature map (all phases delivered)

- **B — Auth**: welcome with staggered entrance; owner email login;
  staff code+PIN with remembered code and auto-submit PIN cells; sessions
  in EncryptedSharedPreferences forever (custom supabase-kt SessionManager);
  optimistic cold-start under the native splash; revoked-session boot-out.
- **C — Owner core**: dashboard (animated revenue, vs-yesterday delta,
  **new:** peak-hour / best-day / GST insight chips, stacked payment bar,
  14-day tappable canvas trend, top items, multi-outlet switcher);
  reports (range chips + date-range picker, **new:** compare vs previous
  equal period, sortable items, expenses, **new:** bill search + payment
  filters, PDF + CSV export via PdfDocument/FileProvider); paper-slip
  receipt view + share-as-PDF.
- **D — Staff management**: restaurant-code hero (copy/share), team list,
  add/edit sheet, 3 presets + 9 permission switches, PIN reveal-once,
  reset/deactivate/remove with confirms.
- **E — Staff experience**: permission-driven tabs; masked-revenue mode
  (counts + % only); animated Orders teaser; profile with access summary;
  live permission refresh on every staff-data response.
- **F — Account**: subscription card with status badge and one
  context-aware billing action via Chrome Custom Tab handoff
  (`magicbill.in/auth/mobile-handoff`), silent license re-fetch on return;
  device-lock info; **new:** dark/light theme toggle (persisted).
- **G — Auto-update**: version.json check on open + manual; DownloadManager
  progress; system installer hand-off; unknown-sources deep link; 24h
  dismissal with Account-tab dot.
- **H — Performance**: single cache-first engine (`CachedQuery` + Room
  `kv_cache`) behind every screen — cached data renders instantly, network
  refreshes silently, failures show a quiet "Updated Xh ago" chip. 8s
  network budget everywhere. R8 minified + resource-shrunk release.

## Toolchain

Gradle 9.4.1 · AGP 9.2.1 (built-in Kotlin upgraded to 2.3.21) · KSP 2.3.10 ·
Hilt 2.60.1 · Compose BOM 2026.06.01 · supabase-kt 3.6.0 · Room 2.8.4 ·
minSdk 26, targetSdk 36, compileSdk 37. Charts are hand-built Compose Canvas
(no chart library shipped). Payment-mode colors are the CVD-validated set
from the RN app, entity-fixed.

## Release engineering

- `.github/workflows/release.yml`: on `v*` tags — builds + signs + attaches
  `magic-bill.apk` and `version.json` **when** `ANDROID_KEYSTORE_BASE64` /
  `ANDROID_KEYSTORE_PASSWORD` secrets and the `SUPABASE_ANON_KEY` repo var
  are set; green-skips otherwise (verified: tag run passed in 10s).
- v2.0.0 was built and signed **locally** and published with `gh release
  create` — permalink verified serving the new APK (HTTP 200, 3.9 MB) and
  version.json.
- `keys/`, `local.properties`, `*.keystore` gitignored — verified nothing
  secret is committed.

## Manual steps remaining (optional)

1. **CI signing** (only if you want tag-push releases without this PC):
   add repo secrets `ANDROID_KEYSTORE_BASE64` (base64 of
   `keys/magic-bill-release.keystore`), `ANDROID_KEYSTORE_PASSWORD`, and
   repo variable `SUPABASE_ANON_KEY`.
2. **On-phone check**: phones were asleep/off wireless ADB at release
   time. Open the app on your phone — existing installs will offer the
   v2.0.0 update by themselves (or install once from the QR/link).
3. FCM push notifications: hook is stubbed in `MagicBillApp.kt` for a
   later phase.

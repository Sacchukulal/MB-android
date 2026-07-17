# Magic Bill Mobile App — Build Report (Phase 5)

Built 2026-07-16/17. Android-only React Native app in `MB-android/`,
published at https://github.com/Sacchukulal/MB-android with a signed-APK
release pipeline and in-app auto-update. Current release: **v1.0.2**
(38.7 MB, arm64, R8-optimized).

## What's built

### Two-door login
- **Owner door**: Supabase Auth email+password (same identity as
  magicbill.in). Multi-outlet owners get a restaurant picker; the selection
  persists. No in-app signup — the app links to the website, where payment
  lives.
- **Staff door**: Restaurant Code + 4-digit PIN via the `staff-login` Edge
  Function. Staff receive an opaque session token — never the license key,
  never direct database access.
- **Sessions persist forever.** On launch the app enters instantly from the
  stored session; verification happens in the background (owner revocations
  still apply immediately with an "Access revoked" message). Only the
  explicit Logout button ends a session; staff logout also deletes the
  server session row. Tokens live in Android Keystore (expo-secure-store).

### Owner experience
- **Dashboard**: today's revenue (computed live from `bills` — never waits
  for the nightly summary job), delta vs yesterday, bill count, avg bill,
  payment split, 14-day trend (tap a bar for its value), top 5 items,
  pull-to-refresh.
- **Reports**: Today / Yesterday / This week / This month / Custom range
  (IST day boundaries matching the POS), sales summary, payment modes,
  item-wise sales, expenses + net, tappable bill list.
- **Bill receipts**: any bill renders as a thermal receipt; Share generates
  a PDF on-device into the Android share sheet. Reports also export as PDF
  or CSV — all on-device, zero server storage.
- **Staff management**: restaurant code card (copy button, auto-generates
  if missing), add/edit staff with free-text role labels, per-person
  permission checklist (9 keys + presets: Full access / Reports only /
  Minimal), auto-generated PINs revealed once, one-tap PIN reset,
  deactivate (kills sessions instantly) / reactivate / remove.
- **Account**: restaurant info, full plan display with status badge,
  next billing + days-left, plan option cards, context-aware billing
  buttons (Subscribe Now / Resubscribe / Manage / Switch Plan) opening
  magicbill.in in a Chrome Custom Tab with automatic sign-in (token
  handoff), bound billing PC info, owner phone registration, theme toggle,
  manual update check.

### Staff experience
- Tabs appear only for enabled permissions: Home (always), Reports
  (`view_reports`), Orders (`take_orders` — polished "coming soon" screen),
  Profile (always).
- `view_revenue_totals` off ⇒ amounts show `••••`, payment split becomes
  percentages, the trend becomes a relative "busy days" shape (the server
  never sends rupee values), item revenue hidden. Individual bill amounts
  ride with `view_bills` (staff handle paper receipts anyway).
- Exports for staff require `export_reports` AND visible amounts.

### Permission system
- Flat owner-controlled `permissions` jsonb per staff row; missing keys are
  false, so future keys (Phase 6 `take_orders`) need no migration.
- Checked **twice**: client-side (`useCan` / `PermissionGate`) for UI, and
  server-side in the Edge Functions for security. Every `staff-data`
  response returns fresh permissions, so owner edits apply on the staff
  member's next fetch.

### Performance & offline
- **Cache-first everywhere**: every screen renders its last data instantly
  from expo-sqlite and refreshes in the background; an "Offline — last
  updated X ago" chip appears when the network is down.
- All network calls carry hard 8–10s timeouts; startup navigates
  optimistically from the stored session. Airplane-mode opens are instant.

### Theming
- Dual light/dark theme (dark = brand default) via CSS variables; the
  toggle (welcome screen, Account, staff Profile) persists across launches
  and logouts. Chart palettes validated for color-vision safety in both
  themes.

### Auto-update
- On launch (and manually from Account) the app fetches
  `releases/latest/download/version.json`, compares semver, and shows a
  dismissable modal with release notes; a slim banner lingers after
  dismissal. "Update now" opens the APK download in the browser. Never
  force-updates.

## Backend additions (MB-backend)
- Migration `0007_staff_system.sql`: `licenses.restaurant_code` (+ SQL
  generator + backfill), `staff`, `staff_sessions`, RLS (owner read-only on
  own staff; sessions service-role only).
- Migration `0008_mobile_device.sql`: `owners.mobile_device_*` columns —
  owner phone registration, POS-style.
- Edge Functions (all deployed): `staff-login`, `staff-session`
  (verify/logout), `staff-manage` (owner-JWT CRUD, duplicate-PIN guard),
  `staff-data` (staff views, server-side masking), `mobile-device`
  (owner phone registration).
- All verified live: wrong PIN rejected, revocation immediate, anon reads
  return zero rows, masked staff never receive amounts, 401 without owner
  JWT.

## Release pipeline
- `.github/workflows/release.yml`: on `v*` tags → `expo prebuild` →
  arm64-only + R8 config → `gradlew assembleRelease` → zipalign + apksigner
  with the release keystore (repo secrets) → GitHub Release with
  `magic-bill.apk` + generated `version.json`. When the signing secrets are
  NOT configured the job exits green with a notice (releases are then built
  and signed locally — the current flow).
- **Keystore**: `MB-android\keys\magic-bill-release.keystore` (password in
  `keystore-password.txt` next to it; folder is gitignored). BACK THESE
  UP — losing them means existing installs can't update in place. Never
  commit them.
- Repo secrets (optional, enables CI builds — run
  `keys\setup-github-secrets.ps1`): `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`; repo variable `SUPABASE_ANON_KEY`.
- To release: bump `expo.version` + `versionCode` in app.json → commit →
  `git tag -a v1.x.y -m "notes"` → `git push origin v1.x.y`.
- Local build recipe: `npx expo prebuild --platform android --clean
  --no-install`, patch `android/gradle.properties`
  (`reactNativeArchitectures=arm64-v8a`), `gradlew assembleRelease`,
  zipalign + apksigner from build-tools, `gh release create` with the APK
  and a matching `version.json`.

## How to run locally
```bash
npm install
cp .env.example .env.local   # Supabase URL + anon key
npx expo start               # press "a" for Android
```
Useful checks: `npx tsc --noEmit`, `npx expo export --platform android`.

## Test checklist (manual, on device)
- Owner login → dashboard → reports → bill → share receipt PDF.
- Staff login (get code+PIN from the owner's Staff tab).
- Owner: add staff with "Reports only" → staff sees only those tabs.
- Owner: toggle off "See revenue amounts" → staff sees ••••/percentages.
- Owner: deactivate staff → staff app boots to "Access revoked".
- Account → Manage Subscription → lands signed-in on magicbill.in billing.
- Restaurant switcher (owners with 2+ licenses).
- Airplane mode → app opens instantly with cached data + offline chip.
- Logout → welcome screen; sessions really cleared.

## Notes & constraints
- APK targets 64-bit arm phones (~2018+). Very old 32-bit devices can't
  install; publish a universal APK alongside if ever needed.
- R8 minification is enabled — smoke-test each release build (login,
  reports, PDF share) since minification can affect release-only paths.

## Deferred (by design)
- **Ordering (Phase 6)**: navigation slot + `take_orders` permission +
  coming-soon screen already in place; plugs in without restructuring.
- **Push notifications**: no daily pushes this phase. Clean insertion
  points: the startup flow in `src/app/_layout.tsx` (register after
  `ready`), and a future notification-preferences row in Account/Profile.
- Play Store distribution (direct APK by decision).

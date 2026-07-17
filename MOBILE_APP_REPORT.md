# Magic Bill Mobile App â€” Build Report (Phase 5)

Built 2026-07-16. Android-only React Native app in `MB-android/`, published
at https://github.com/Sacchukulal/MB-android with a signed-APK release
pipeline and in-app auto-update.

## What's built

### Two-door login
- **Owner door**: Supabase Auth email+password (same identity as
  magicbill.in). Multi-outlet owners get a restaurant picker; the selection
  persists. No in-app signup â€” the app links to the website, where payment
  lives.
- **Staff door**: Restaurant Code + 4-digit PIN via the `staff-login` Edge
  Function. Staff receive an opaque session token â€” never the license key,
  never direct database access.
- **Sessions persist forever.** On launch: stored owner sessions silently
  refresh; staff tokens re-verify server-side (owner revocations apply
  immediately, with an "Access revoked" message). Only the explicit Logout
  button ends a session; staff logout also deletes the server session row.
  Tokens live in Android Keystore (expo-secure-store).

### Owner experience
- **Dashboard**: today's revenue (computed live from `bills` â€” never waits
  for the nightly summary job), Î” vs yesterday, bill count, avg bill,
  payment split, 14-day trend (tap a bar for its value), top 5 items,
  pull-to-refresh.
- **Reports**: Today / Yesterday / This week / This month / Custom range
  (IST day boundaries matching the POS), sales summary, payment modes,
  item-wise sales, expenses + net, tappable bill list.
- **Bill receipts**: any bill renders as a thermal receipt; Share generates
  a PDF on-device into the Android share sheet. Reports also export as PDF
  or CSV â€” all on-device, zero server storage.
- **Staff management**: restaurant code card (copy button, auto-generates
  if missing), add/edit staff with free-text role labels, per-person
  permission checklist (9 keys + presets: Full access / Reports only /
  Minimal), auto-generated PINs revealed once, one-tap PIN reset,
  deactivate (kills sessions instantly) / reactivate / remove.
- **Account**: restaurant info, plan + status badge (matches website
  semantics), next billing + days-left, bound billing PC (name, HWID
  prefix, last seen), "Manage subscription" link to the website, theme
  toggle, manual update check.

### Staff experience
- Tabs appear only for enabled permissions: Home (always), Reports
  (`view_reports`), Orders (`take_orders` â€” polished "coming soon" screen),
  Profile (always).
- `view_revenue_totals` off â‡’ amounts show `â€¢â€¢â€¢â€¢`, payment split becomes
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

### Offline
- Every fetch caches into expo-sqlite; when the network fails, screens show
  cached data with an "Offline â€” last updated X ago" chip. Airplane-mode
  app opens work for both doors.

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
- Edge Functions (all deployed): `staff-login`, `staff-session`
  (verify/logout), `staff-manage` (owner-JWT CRUD, duplicate-PIN guard),
  `staff-data` (staff views, server-side masking).
- All verified live: wrong PIN rejected, revocation immediate, anon reads
  return zero rows, masked staff never receive amounts, 401 without owner
  JWT.

## Release pipeline
- `.github/workflows/release.yml`: on `v*` tags â†’ `expo prebuild` â†’
  `gradlew assembleRelease` â†’ zipalign + apksigner with the release
  keystore (repo secrets) â†’ GitHub Release with `magic-bill.apk` +
  generated `version.json`.
- **Keystore**: `C:\Data_Drive\MagicBill\MB-android\keys\magic-bill-release.keystore`
  (password in `keystore-password.txt` next to it). BACK THESE UP â€” losing
  them means existing installs can't update in place. Never commit them.
- Repo secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`;
  repo variable `SUPABASE_ANON_KEY` (public anon key).
- To release: bump `expo.version` in app.json â†’ commit â†’
  `git tag -a v1.x.y -m "notes"` â†’ `git push origin v1.x.y`.

## How to run locally
```bash
npm install
cp .env.example .env.local   # Supabase URL + anon key
npx expo start               # press "a" for Android
```
Useful checks: `npx tsc --noEmit`, `npx expo export --platform android`.

## Test checklist (manual, on device)
- Owner login â†’ dashboard â†’ reports â†’ bill â†’ share receipt PDF.
- Staff login (get code+PIN from the owner's Staff tab).
- Owner: add staff with "Reports only" â†’ staff sees only those tabs.
- Owner: toggle off "See revenue amounts" â†’ staff sees â€¢â€¢â€¢â€¢/percentages.
- Owner: deactivate staff â†’ staff app boots to "Access revoked".
- Restaurant switcher (owners with 2+ licenses).
- Airplane mode â†’ app opens with cached data + offline chip.
- Logout â†’ welcome screen; sessions really cleared.

## Deferred (by design)
- **Ordering (Phase 6)**: navigation slot + `take_orders` permission +
  coming-soon screen already in place; plugs in without restructuring.
- **Push notifications**: no daily pushes this phase. Clean insertion
  points: the startup flow in `src/app/_layout.tsx` (register after
  `ready`), and a future notification-preferences row in Account/Profile.
- Play Store distribution (direct APK by decision).


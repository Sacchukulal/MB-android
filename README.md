# Magic Bill — Android

The mobile companion app for [Magic Bill](https://magicbill.in) restaurant
billing: live sales reports for owners, PIN-based access for staff.

## Install

Download `magic-bill.apk` from the
[latest release](https://github.com/Sacchukulal/MB-android/releases/latest)
and open it on your Android phone. The app checks for updates automatically
and offers new versions in-app.

## Two ways to sign in

- **Owner** — the same email & password as the magicbill.in portal. Full
  dashboard, date-range reports, bill receipts with PDF sharing, staff
  management, plan status.
- **Staff** — a Restaurant Code (e.g. `HH-4829`) plus a 4-digit PIN, both
  provided by the owner. What staff can see is controlled entirely by the
  owner through per-person permission toggles.

## Development

```bash
npm install
cp .env.example .env.local   # fill in the Supabase URL + anon key
npx expo start
```

Expo SDK 57 · expo-router · NativeWind · Supabase. Android only.

## Releases

Bump `expo.version` in `app.json`, then push a tag:

```bash
git tag -a v1.0.1 -m "What changed"
git push origin v1.0.1
```

GitHub Actions builds the APK, signs it, and publishes the release with a
`version.json` that in-app updaters read.

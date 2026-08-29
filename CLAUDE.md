# MB-android (Kotlin + Jetpack Compose) — 3.x, rebuilt 2026-08-28

The phone is a screen. The cloud (`MB-backend/docs/PHONE_API.md`) is the owner's window; the
counter over the shop's WiFi (`MB-pos/docs/LAN_PROTOCOL.md`) is the floor. The plan and the
decisions are in `../docs/ANDROID_ROUND.md`.

## Commands

- Build: `.\gradlew.bat :app:assembleDebug` with `JAVA_HOME` = Android Studio's jbr. From
  bash: `cd MB-android && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew …`
  — **always with an explicit `cd`; the shell's directory drifts.**
- Tests: `.\gradlew.bat :app:testDebugUnitTest` (JUnit + Robolectric for Room). The hygiene
  test bans keys, the old library, raw colours outside `ui/theme/Palette.kt`, Material tokens in screens, a second kit, realtime, and Edge Functions
  other than `staff-login`.
- Install + drive: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`,
  `adb shell am start -n com.magicbill.app/.MainActivity`, `adb exec-out screencap -p > shot.png`.
- Secrets live in `local.properties` (gitignored): `SUPABASE_URL`, `SUPABASE_ANON_KEY` (the
  NEW project `grjhdszcvuomgluaqncf`), `MB_KEYSTORE_*`. Never hardcode; never commit `keys/`.
- Release signing MUST use `keys/magic-bill-release.keystore` (alias `magicbill`) — the same
  cert as every published build, or phones cannot update in place. Debug builds are signed
  with it too. `versionCode` only ever goes up (17 = 3.0.0).

## Shape

`core/` money (paise → text, Indian grouping), IST days, Argon2id (the counter's parameters),
`Answer` (Ok / Refused / Unreachable / SignedOut), frozen-bill JSON.
`cloud/` `CloudLink` (OkHttp; password + `staff-login`; PostgREST RPC/REST; one refresh on 401),
`SessionStore`, `Account` (restaurants, the chosen one), `Mirror` (ONE loop over 14 tables with
a cursor each), `Sync` (when to pull), `People` (the staff desk), `ReportMath` (sums only).
`counter/` `CounterLink` (pinned TLS, every LAN route, the WebSocket), `Counter` (pairing,
credential, `/v1/me`), `Floor` (catalogue cache, the durable intent queue, the floor push),
`Stream` (the socket's lifetime), `Discovery` (mDNS).
`db/` Room: the mirror tables, cursors, intents, the floor cache. `prefs/` `Secure` (keystore
box; `KeyBox` is its seam) and `Plain`.
`ui/theme` ONE palette (`Palette.kt`), ONE scale (`Tokens.kt`); Material's scheme and typography are
derived from them in `Theme.kt`. `ui/kit` the only components a screen may use — the hygiene
test forbids `MaterialTheme.colorScheme`/`typography` in screens and any second component set;
`ui/screens/*` one screen set for everybody, filtered by permission; `nav/` type-safe routes.

## Rules

- **No money is computed on the phone.** Reports are sums of `day_*` totals; a bill shows the
  counter's frozen columns; an order shows the counter's outcome. The one exception the contract
  names is a line's gross on a receipt (frozen price × frozen qty).
- **Cache first, one emission.** Every screen draws from Room; a pull is silent; a spinner never
  covers cached content. Pull on open (if stale), pull-to-refresh, screen open. Never a timer.
- **Nothing metered but `staff-login`, once per install.** No realtime, no other Edge Function.
- **Every network call has a deadline** from the OkHttp client; every answer is an `Answer`.
- **Intents are durable before their first send; their id is kept across restarts; outcomes
  are final.** A retry uses the same id (`flush`); a held one is released with a NEW id.
- **The socket lives with the app**, not with a screen: open while paired and in front, 60 s
  linger after background, reopened on return (`Stream.ensure()`). No screen may toggle it —
  a per-screen switch once left the phone deaf 60 s after every tab hop. The counter counts
  the phone as live while it is open.
- **Sending never waits on a screen.** `Floor.stageOrder` writes the whole order in ONE
  transaction, shows it on the floor as a sending tile, and flushes in the app scope; the
  counter's sentence arrives on `Floor.sentences` and the shell shows it once, at the top.
- **The floor is the whole floor.** Every open order is on the phone (`by`/`mine` say whose);
  tapping any taken table opens that order.
- **The counter's sentences are shown as-is.** The phone composes nothing on top of a refusal.
- **Colour is never the only signal**; every raw hex lives in `ui/theme/Palette.kt`.
- StrictMode kills a debug build that touches the network on the main thread.

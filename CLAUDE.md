# MB-android (Kotlin + Jetpack Compose)

- Build: `.\gradlew.bat :app:assembleDebug` with `JAVA_HOME` set to Android
  Studio's jbr. This machine sets `NoDefaultCurrentDirectoryInExePath=1` —
  always invoke as `.\gradlew.bat`, never bare `gradlew.bat`.
- Secrets live in `local.properties` (gitignored): SUPABASE_URL,
  SUPABASE_ANON_KEY, MB_KEYSTORE_*. Never hardcode; never commit `keys/`.
- Release signing MUST use `keys/magic-bill-release.keystore` (alias
  `magicbill`) — same cert as all published builds or in-place updates break.
  Debug builds are also signed with it so they install over the released app.
- Design language: open canvas — no cards-in-cards. Sections separate with
  `SectionHeader` + whitespace; lists use `ListRow`; motion tokens in
  `ui/theme/Motion.kt`. Payment-mode colors are entity-fixed in
  `ui/theme/Color.kt` (CVD-validated) — don't repaint them.
- Data rule: OWNER dashboard/reports/bill-detail read from the local SQLite
  mirror (`OwnerLocalDao`, topped up by `OwnerSync` — last synced data is
  always available offline, any range). Staff + account screens read through
  `CachedQuery` (cache-first, silent refresh). Never block cached content
  with a spinner.
- Staff clients never receive the license key; all staff data flows through
  Edge Functions (`staff-login`, `staff-data`, `staff-manage`).
- ONE profile screen serves both worlds (`ui/screens/profile/ProfileScreen.kt`,
  2.4.5). The header, theme toggle, "This phone" line, update button and
  log-out block are written once; only the middle differs, chosen by a sealed
  `ProfileSession`. Do not fork it again — that drift is what produced a staff
  screen with no update button and no version number. The licence key is
  rendered inside the Owner branch only, so no staff code path can draw it.
- Connection lifetimes (`OrdersRealtime`, 2.4.5) — TWO LINES, and they are
  deliberately different:
  - **presence** (`orders-<room>`) is held for the WHOLE FOREGROUND SESSION of
    any session with ordering access, not per screen. It is what makes the
    counter's phone count steady.
  - **live** (`orders-<room>-live`) is held only while an Orders surface is on
    screen, plus a **60-second linger**. The linger is what makes navigation
    free: `release()` schedules a stop, the next `acquire()` cancels it and
    reuses the channel with no rejoin, no re-read and no presence churn.
  - Backgrounding starts the same 60s timer for both, from
    `ProcessLifecycleOwner` — never from screen-level effects, and remember
    that `addObserver` REPLAYS the current state on registration.
  - A pending stop carries a token checked inside the lock, so it can never
    kill a line rebuilt since (including for a new room).
  Never make either line refcounted by the screens again: Compose Navigation
  pauses the outgoing screen before the incoming one resumes, so the count
  passes through zero on every single navigation. Measured cost of that:
  61 rejoins and 119 presence messages per ten minutes of ordering.

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

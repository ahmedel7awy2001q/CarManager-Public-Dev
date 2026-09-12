# CarManager Transfer Manifest

Status: **READY FOR REVIEWED PRODUCT TRANSFER — temporary public-development repository**

## Starting point

- Official public-safe archive: `CarManager_PUBLIC_SAFE_2026-09-12.zip`
- Archive SHA-256: `cd9bac91a1a15d2d821fc0727f1018aa8dc55e929f36a4fd9ad15cdaef12bea8`
- Private-source baseline reference recorded by the sanitized snapshot: `5fded81925d40a4819484aacdad4c90552220b19`
- Last verified product-source reference recorded by the sanitized snapshot: `1eeaab07d3d401577e4df26c82718e83c8702c03`
- Fresh public Git history baseline commit: `f1e535eea3bddff9d313269f490ca064e80a7949`
- Clean transfer branch for this batch: `product/main-alerts-branding-2026-09-12`
- Branch base: public-safe `main` at `69f1ba9fe6009e175175b0715156f62aca670fa2`

## Latest validated PRODUCT head

`1d7fadaaff6fa1d3294e6e4a43170c88a4cf1378` — `[PRODUCT] Replace premium tagline with designer credit`

Validated by GitHub Actions run `34695589693`.

## PRODUCT commits to transfer, in order

1. `e0ac49dec2d796aecd9d0e112eeeb01f443729c2` — `[PRODUCT] Add prioritized maintenance and attention notification policy`
2. `5d4b31c06e17604fac5cc690d920ee98f4a77b57` — `[PRODUCT] Deliver prioritized vehicle attention notifications`
3. `497ece34aeb2b23f57bf43dae34b5fca91dad2f6` — `[PRODUCT] Check alerts immediately after notification permission grant`
4. `82f010c5dea57ce08ff7b6e3791da022d107e100` — `[PRODUCT] Test maintenance lead times and alert priorities`
5. `1d7fadaaff6fa1d3294e6e4a43170c88a4cf1378` — `[PRODUCT] Replace premium tagline with designer credit`

Do not substitute commits from the older experimental branch `product/update-batch-2026-09-12`; this clean branch was intentionally rebuilt from public-safe `main` for transfer to the real application.

## PUBLIC-ONLY commits in the sanitized public history

1. `f1e535eea3bddff9d313269f490ca064e80a7949` — `[PUBLIC-ONLY] Import sanitized public-safe baseline`
2. `3d07ef273adfb1d2bbfb6c71fd4b964426f341f8` — `[PUBLIC-ONLY] Add guarded public Android CI`
3. `604d4d96a106c7170f609cfb125f7685e4d6ce0f` — `[PUBLIC-ONLY] Add transfer manifest tracking`
4. `69f1ba9fe6009e175175b0715156f62aca670fa2` — `[PUBLIC-ONLY] Record successful public CI validation`

This manifest refresh is also PUBLIC-ONLY and must not be transferred to production.

## PRODUCT files changed in this batch

- `app/src/main/java/com/ahmed/carmanager/notifications/AttentionNotificationPolicy.kt` — new alert policy for maintenance, important faults, vehicle license, insurance, inspection and manual reminders.
- `app/src/main/java/com/ahmed/carmanager/notifications/ReminderWorker.kt` — background evaluation, priority channels, de-duplication/rate limiting, six-hour periodic check and immediate startup check.
- `app/src/main/java/com/ahmed/carmanager/MainActivity.kt` — re-check alerts immediately after Android notification permission is granted.
- `app/src/main/java/com/ahmed/carmanager/ui/PremiumDashboardScreen.kt` — replaces `Premium Automotive` with `تصميم احمد الحاوي` under `CarManager`.
- `app/src/test/java/com/ahmed/carmanager/notifications/AttentionNotificationPolicyTest.kt` — coverage for user-selected maintenance lead time and notification priority tiers.

No Room entity/schema/migration file changed in this batch.

## Behavior implemented

- Maintenance notifications use the existing per-plan `warningBeforeKm` and `warningBeforeDays` values. The maintenance editor already lets the user choose these values and select priority `عادية / مهمة / عالية`.
- Maintenance overdue is urgent. Due-soon maintenance is important; priority `10` raises it to urgent and priority `5` keeps it important.
- Open `HIGH` faults generate important notifications; open `CRITICAL` faults generate urgent notifications. Resolved/closed faults do not notify.
- Vehicle license, insurance and inspection expiration are monitored. Expired items are urgent. The vehicle license is treated more strongly near expiry; insurance/inspection move from reminder to important as expiry becomes close.
- Other document types such as receipts/contracts do not generate expiry notifications from this policy.
- Manual reminders preserve their existing date/km lead time and priority values.
- Notification delivery is state-aware: unchanged urgent alerts repeat at most daily, important alerts every two days, normal reminders weekly; a meaningful state change can notify on the next check.
- Only active/secondary vehicles are evaluated.
- Android 13+ notification permission is respected.

## Validation results

Validated GitHub Actions run: `34695589693` on PRODUCT head `1d7fadaaff6fa1d3294e6e4a43170c88a4cf1378`.

- Public-safe guard: **PASS**.
- JDK 17 / Android SDK / Gradle 8.9 setup: **PASS**.
- Unit tests (`:app:testDebugUnitTest`): **PASS**.
- Lint (`:app:lintDebug`): **PASS**.
- Debug build (`:app:assembleDebug`): **PASS**.
- Reports artifact upload: **PASS**.
- PublicDev APK artifact upload: **PASS**.
- Production Firebase/signing: not restored to the public repository.
- Debug `.publicdev` isolation: retained.
- Room schema/migrations: unchanged by this batch.

## Remaining notes

- The PublicDev APK remains a sanitized test build and is not the production application. Production Google/Firebase authentication must be validated only after these PRODUCT commits are transferred into the private CarManager repository.
- Kilometer-based notification accuracy depends on the current odometer stored for the selected vehicle.
- Android WorkManager periodic execution is not an exact alarm; Android may shift the six-hour background check according to battery/system scheduling. The immediate startup check covers app-open scenarios.
- The older experimental branch contains unrelated search work and must not be merged wholesale into the private repository.

## Transfer instructions to CarManager Private

1. Do **not** merge the public repository or either public branch wholesale.
2. Open the private CarManager repository and create a reviewed transfer branch from its current intended production/development HEAD.
3. Cherry-pick only the five `[PRODUCT]` commits listed above, in the exact listed order.
4. Do not cherry-pick this manifest or any `[PUBLIC-ONLY]` commit.
5. If a conflict appears, the private repository is authoritative for production Firebase, signing, release configuration and private operational metadata. Never replace those private files with public placeholders.
6. Run private unit tests, lint and debug build after the cherry-picks. Then verify real Google login, existing vehicle data, maintenance editing, Android notification permission and a test due-soon alert.
7. Because this batch makes no Room schema change, do not introduce a migration solely for these features.

# CarManager Transfer Manifest

Status: **COMPLETE — validated public-safe development batch**

## Starting point

- Official public-safe archive: `CarManager_PUBLIC_SAFE_2026-09-12.zip`
- Archive SHA-256: `cd9bac91a1a15d2d821fc0727f1018aa8dc55e929f36a4fd9ad15cdaef12bea8`
- Private-source baseline reference recorded by the sanitized snapshot: `5fded81925d40a4819484aacdad4c90552220b19`
- Last verified product-source reference recorded by the sanitized snapshot: `1eeaab07d3d401577e4df26c82718e83c8702c03`
- Fresh public Git history baseline: `f1e535eea3bddff9d313269f490ca064e80a7949`
- Product-development starting point: `69f1ba9fe6009e175175b0715156f62aca670fa2`
- Public feature branch: `product/update-batch-2026-09-12`

## Final product head for transfer

`3f5a9f4239c19972f2dba87c2da339d89cd1a6ea` — `[PRODUCT] Use storefront aliases across unified parts sources`

The public-only cleanup immediately after the final product commit is:

`76948d2baceaa72f78ead5253b57b8958a9fdcb1` — `[PUBLIC-ONLY] Remove temporary complete-batch tooling`

Manifest-refresh/checksum-correction commits are PUBLIC-ONLY bookkeeping and must not be transferred to production. The final product head above is the authoritative endpoint for the transferable sequence.

## PRODUCT commits to transfer — exact order

1. `ed2484c78ad1863853fb73525b68a9a4068267f9` — `[PRODUCT] Add storefront vehicle alias catalog`
2. `03e14fe2b7cd5339c90e81830219691091ae6c16` — `[PRODUCT] Feed storefront aliases into vehicle identity`
3. `ebf9bb6651ebaaecfb223ee540509f2a7ac5387c` — `[PRODUCT] Add unified Auto Spare vehicle-aware adapter`
4. `f76bd7f64ff84569b90611e2abc09d28912e88df` — `[PRODUCT] Route core parts search through vehicle aliases`
5. `33c260e656bca1ff0352675c542809e5751f0912` — `[PRODUCT] Test unified storefront alias search`
6. `0261854cd28caaabeeb66bee8c271c603c6d2aa2` — `[PRODUCT] Align New Logan aliases with Auto Spare catalog`
7. `5486d8698c52c664719c2952704546424bd7ee67` — `[PRODUCT] Align unified search tests with store model years`
8. `0fa3ae86d242fcf5fda122c62cd926a067e6c17a` — `[PRODUCT] Fix and broaden unified Auto Spare search`
9. `ece480ce4c4f5a1106a336bcf46f0506fcffd590` — `[PRODUCT] Add foreign vehicle result guard`
10. `25566695e286e9cf79e66a35a7b7ee5d09d6ee98` — `[PRODUCT] Reject clearly foreign vehicle search results`
11. `8f628ac20f9966053cce8bc407c989bb9a9a9e07` — `[PRODUCT] Test foreign-result suppression in unified search`
12. `558025737b53c469f894db53cc8b4e94fe081717` — `[PRODUCT] Add unified attention notification policy`
13. `302e830fdc78483efbc3f6eda8a6a52ffe46c2c3` — `[PRODUCT] Upgrade reminders to smart attention notifications`
14. `a22c3fafe992f774845283c45a91feefe61717b2` — `[PRODUCT] Test smart maintenance and attention alerts`
15. `5e2f0a3e5c90e48077937d68ecd30036bcfaf5ed` — `[PRODUCT] Check alerts immediately after notification permission grant`
16. `62ada9e36d3c83b0873dd19f7330599148ee4714` — `[PRODUCT] Replace legacy reminder schedule safely on upgrade`
17. `8b1b4c1b18cf75afb1a3ecde8f1223044a9ca0f6` — `[PRODUCT] Fix notification permission callback signature`
18. `0d730084913c86376902662efd0adc0c7622e6ed` — `[PRODUCT] Add safe vehicle lifecycle and preserve garage drafts`
19. `05e73eaf489ea68c70619c722885b0aab174b852` — `[PRODUCT] Clarify technical catalog coverage and update failures`
20. `3f5a9f4239c19972f2dba87c2da339d89cd1a6ea` — `[PRODUCT] Use storefront aliases across unified parts sources`

Do not omit, reorder, squash, or mix these commits with PUBLIC-ONLY commits during the private transfer unless a conflict review explicitly requires a different strategy.

## PUBLIC-ONLY commits — do not transfer

Initial public-safe setup:

1. `f1e535eea3bddff9d313269f490ca064e80a7949` — `[PUBLIC-ONLY] Import sanitized public-safe baseline`
2. `3d07ef273adfb1d2bbfb6c71fd4b964426f341f8` — `[PUBLIC-ONLY] Add guarded public Android CI`
3. `604d4d96a106c7170f609cfb125f7685e4d6ce0f` — `[PUBLIC-ONLY] Add transfer manifest tracking`
4. `69f1ba9fe6009e175175b0715156f62aca670fa2` — `[PUBLIC-ONLY] Record successful public CI validation`

Temporary completion tooling / bookkeeping:

5. `b67902573ef75500ccb8db825ea8b564832948a3` — `[PUBLIC-ONLY] Apply remaining CarManager product batch` — initial temporary workflow attempt; no product source change.
6. `b6a4d7d656cf6b29091b0245d5ae8edaef61f9c2` — `[PUBLIC-ONLY] Add remaining product batch patcher`
7. `0fb17d9333991a8445717572152f3f03acd678ee` — `[PUBLIC-ONLY] Run remaining CarManager product batch`
8. `76948d2baceaa72f78ead5253b57b8958a9fdcb1` — `[PUBLIC-ONLY] Remove temporary complete-batch tooling`
9. `d2411b6052a3bb9fdcbe6cdc63cbda1b146c40aa` — `[PUBLIC-ONLY] Finalize product transfer manifest`
10. This checksum-correction commit — `[PUBLIC-ONLY] Correct final APK checksum in manifest`

The temporary complete-batch workflow and patcher were removed from the branch after successful validation.

## Product files changed after the product-development starting point

- `app/src/main/java/com/ahmed/carmanager/MainActivity.kt`
- `app/src/main/java/com/ahmed/carmanager/data/catalog/VehicleCatalogUpdateManager.kt`
- `app/src/main/java/com/ahmed/carmanager/data/repository/RoomVehicleRepository.kt`
- `app/src/main/java/com/ahmed/carmanager/data/repository/VehicleRepository.kt`
- `app/src/main/java/com/ahmed/carmanager/notifications/AttentionNotificationPolicy.kt`
- `app/src/main/java/com/ahmed/carmanager/notifications/ReminderWorker.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/AutoSpareUnifiedAdapter.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/CarManagerScreenHost.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/CarManagerViewModel.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/ExpandedPartsPriceEngine.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/ForeignVehicleTitleGuard.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/GarageHistorySheet.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/GarageScreen.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/PartsPricingEngine.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/StorefrontVehicleAliasCatalog.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/VehicleFitmentIntelligence.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicy.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/VehicleMarketIdentity.kt`
- `app/src/main/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverage.kt`
- `app/src/test/java/com/ahmed/carmanager/notifications/AttentionNotificationPolicyTest.kt`
- `app/src/test/java/com/ahmed/carmanager/ui/UnifiedPartsSearchTest.kt`
- `app/src/test/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicyTest.kt`
- `app/src/test/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverageTest.kt`

Public-only infrastructure files are intentionally excluded from the product file list.

## Implemented product behavior

### Unified spare-parts search

- The user selects the saved vehicle and types the part name/OEM only.
- Vehicle make/model/year and Egyptian storefront aliases are generated automatically for supported providers.
- Renault Logan 2021 includes both `Logan / لوجان` and the Auto Spare storefront aliases `New Logan / نيو لوجان` within the verified store year range.
- Auto Spare is vehicle-aware beyond the old Kia-Cerato-only route.
- Clearly foreign-vehicle product titles are rejected; generic part-only offers may remain as explicitly low-confidence discovery results.

### Smart attention notifications

- Maintenance due soon / overdue.
- High or critical unresolved faults.
- Expiring / expired vehicle documents.
- Manual date/odometer reminders.
- Rate limiting prevents repeated notification spam; urgent, important, and ordinary reminders have different repeat windows.
- Periodic checks plus an immediate check after notification permission is granted.
- Separate urgent and normal reminder notification channels.

### Vehicle lifecycle and garage safety

- Operational vehicles are only `ACTIVE` / `SECONDARY` and non-deleted.
- `SOLD` / `ARCHIVED` vehicles move to garage history and are excluded from normal operational selection, context/GPS resolution, and parts vehicle lists.
- Restoring a historical vehicle returns it to the garage.
- Removal from history uses existing soft-delete semantics only after sale/archive; related maintenance/fuel/trip/ownership data is not destructively deleted.
- When the selected vehicle becomes sold/archived/deleted, selection falls back to another operational vehicle.

### Add/Edit draft protection

- Dirty Add/Edit bottom sheets veto the hidden transition.
- Back, swipe, dismiss, close/cancel paths all route through the discard confirmation.
- Choosing `متابعة التعديل` leaves the same in-memory draft open instead of losing it.
- Saving closes normally without a discard warning.

### Vehicle catalog / technical clarity

- Technical coverage is explicitly classified as technically verified, identity known but technically incomplete, or unknown identity.
- Missing engine/transmission codes are not invented.
- Add/Edit UI tells the user when the vehicle identity is known but technical data is incomplete.
- Catalog-update failures now clearly state the reason category and that the bundled/cached catalog remains usable and no local user data was lost.

## Validation results

Final complete-batch validation workflow: GitHub Actions run `34701400475` in `CarManager-Public-Dev`.

The workflow generated the final three PRODUCT commits locally, validated that resulting source tree, uploaded the APK, and then pushed the validated PRODUCT commits ending at `3f5a9f4239c19972f2dba87c2da339d89cd1a6ea`.

- Public-safe baseline guard: **PASS**.
- JDK 17 setup: **PASS**.
- Gradle 8.9 setup: **PASS**.
- Kotlin compile (`:app:compileDebugKotlin`): **PASS**.
- Unit tests (`:app:testDebugUnitTest`): **PASS**.
- Lint (`:app:lintDebug`): **PASS**.
- Debug build (`:app:assembleDebug`): **PASS**.
- Final lifecycle/data-safety invariants: **PASS**.
- Public debug APK artifact upload: **PASS**.
- Validated PRODUCT commit push: **PASS**.
- Room schema remains **version 8**; no migration was added for this batch.
- `fallbackToDestructiveMigration`: **absent**.
- Production release signing files/configuration were **not** added to the public mirror.
- Production Firebase configuration was **not** restored to the public mirror.
- Public debug `applicationIdSuffix = ".publicdev"` remains required and was checked by the validation guard.

### Final public-debug APK

- Artifact name: `CarManager-publicdev-complete-batch`
- Workflow run: `34701400475`
- Artifact ID: `10300358556`
- Artifact ZIP digest reported by GitHub: `sha256:e4f3b32577cfa913fef9e9ed9386df50839a74e7fe7c168690cbc17a48b98b4b`
- Extracted local APK SHA-256 (verified after download): `064e7081d50517fadde7fdacb91f5022786ae319f17b2558c34ca6bf7ea0a0b4`
- Extracted local APK size: `26,086,425` bytes

## Remaining issues / notes

- A real-device regression pass is still recommended before production release, especially Add/Edit swipe/back behavior, sale/archive/restore UI, notification permission behavior, and external store HTML changes.
- Kilometer-based maintenance/reminder accuracy depends on the latest odometer value saved in CarManager.
- Storefront HTML/search routes are external and may change; generic fallback/search behavior is intentionally conservative.
- Production Firebase/Auth/Cloud behavior is intentionally unavailable in the public-safe mirror. Any production-only cloud validation must occur after the PRODUCT commits are transferred back to Private.
- The remote vehicle-catalog update channel still requires a valid production or separately approved public-safe backend source; the bundled/cached catalog remains the offline fallback.
- Production release signing is intentionally unavailable in this public mirror and must never be added here.

## Transfer instructions to CarManager Private

1. Do **not** merge `CarManager-Public-Dev` or its branches wholesale into Private.
2. In the private CarManager repository, create a reviewed transfer branch from the intended current private baseline.
3. Cherry-pick **only** the 20 PRODUCT commits listed above, in the exact order shown.
4. Do **not** cherry-pick any PUBLIC-ONLY commit, workflow, patcher, public Firebase placeholder, public debug suffix configuration, or manifest bookkeeping.
5. If the private branch has diverged, resolve each conflict manually with Private authoritative for production Firebase, signing/release configuration, private metadata, and any newer product behavior.
6. Preserve Room schema/migration history. Do not resolve a conflict by deleting migrations or enabling destructive fallback.
7. Run private compile, all unit tests, lint, migration checks, debug build, release build, production Firebase checks, and release signing verification after cherry-picking.
8. Perform a real-device smoke/regression test with existing user data before production merge/release.
9. Only after those checks should the reviewed transfer branch be merged into the private production line.

# CarManager Product Quality Standard

This document is a permanent development rule for CarManager and should be applied to every release and feature.

## Core rule
Every change must improve the product without sacrificing data safety, compatibility, account isolation, signing continuity, or usability. Do not keep fields, screens, or workflows merely because they already exist; remove or simplify anything that does not add clear user value.

## Stability and compatibility
- Never change the Android applicationId `com.ahmed.carmanager`.
- Keep release signing continuity so every approved release installs over the previous signed version.
- Increase versionCode for installable releases.
- Never use destructive Room migration for normal upgrades. Existing local user data must be preserved.
- Treat cloud restore, migration, import, and account switching as high-risk operations and protect them with ownership checks and clear confirmation.

## Smart maintenance
- Historical maintenance may be entered after the odometer has advanced; saving it must never lower the current odometer.
- Keep actual historical odometer and date independently, regardless of whether due logic uses odometer, date, or whichever comes first.
- Never fabricate missing historical dates.
- Link historical records to plans and calculate next due thresholds from the real event.
- Do not let an older imported record roll the active maintenance anchor backward.
- Infer usage pace from known historical odometer/date and current odometer/date, but keep projections informative rather than authoritative.
- Evaluate automatic maintenance notifications with the same maintenance engine used by the UI.

## Quality gate
A build is not ready until debug compilation, unit tests, permanent signing-certificate verification, signed release compilation and APK signature verification all succeed.

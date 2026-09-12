# v0.3.0 Settings & Maintenance Upgrade

This increment keeps the production compatibility rules unchanged while expanding the professional settings experience.

## Included
- Settings hub grouped by vehicle, maintenance, account/security, cloud data and operational tools.
- Account profile display-name editing, email verification actions, verification refresh and password-reset entry point.
- Smart maintenance editor with odometer/date/whichever-first rules, last service, next due, estimated cost, warning thresholds, priority and notes.
- Search and filtering for maintenance plans, active/disabled state management and dashboard-style maintenance summary.
- Existing vehicle editing remains in the garage flow so the same validated form is reused instead of duplicating vehicle-write logic.

## Compatibility guarantees
- applicationId remains `com.ahmed.carmanager`.
- No Room schema change is introduced in this increment.
- No destructive migration is used.
- Existing maintenance records and vehicle ownership remain authoritative.
- Permanent signing identity remains unchanged.

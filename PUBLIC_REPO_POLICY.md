# CarManager Temporary Public Repository Policy

This repository is a sanitized development mirror. It is **not** the production repository and must never receive production credentials or signing material.

## Commit classification

- `[PRODUCT]` — reviewed product/source changes intended to be transferred later to the private CarManager repository.
- `[PUBLIC-ONLY]` — public-repository infrastructure, CI, placeholder Firebase, safety guards, temporary public configuration, and transfer bookkeeping. Do not cherry-pick these into production unless explicitly reviewed and desired.

Do not mix the two classes in one commit when the changes can reasonably be separated.

## Public-safety rules

- Keep debug `applicationIdSuffix = ".publicdev"` and `versionNameSuffix = "-publicdev"`.
- Never add production `google-services.json`, Maps/API credentials, runtime databases, exports/backups, user data, signing keys, signing passwords, or release signing configuration.
- Use GitHub-hosted standard runners only.
- Product database migrations must remain non-destructive. Do not use destructive migration fallback.
- Run `bash scripts/public_safe_guard.sh` before every push that changes infrastructure or configuration.
- `scripts/verify_public_safe_baseline.sh` is a one-time provenance check for the original sanitized archive; it is not part of recurring CI because legitimate PRODUCT changes modify baseline files.

## Transfer rule

Never merge this public mirror wholesale into the private production repository. Transfer only reviewed `[PRODUCT]` commits in order, then run the private repository's full validation/signing process there.

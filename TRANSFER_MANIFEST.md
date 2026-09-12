# CarManager Transfer Manifest

Status: **ACTIVE — update throughout the temporary public-development phase**

## Starting point

- Official public-safe archive: `CarManager_PUBLIC_SAFE_2026-09-12.zip`
- Archive SHA-256: `cd9bac91a1a15d2d821fc0727f1018aa8dc55e929f36a4fd9ad15cdaef12bea8`
- Private-source baseline reference recorded by the sanitized snapshot: `5fded81925d40a4819484aacdad4c90552220b19`
- Last verified product-source reference recorded by the sanitized snapshot: `1eeaab07d3d401577e4df26c82718e83c8702c03`
- Fresh public Git history baseline commit: `f1e535eea3bddff9d313269f490ca064e80a7949`

## Latest implementation commit

`3d07ef273adfb1d2bbfb6c71fd4b964426f341f8` — `[PUBLIC-ONLY] Add guarded public Android CI`

> A committed manifest cannot contain its own final commit hash without creating another commit. At final hand-off, this field records the latest implementation commit; run `git rev-parse HEAD` for the current manifest/bookkeeping HEAD.

## PRODUCT commits to transfer, in order

_None yet._

## PUBLIC-ONLY commits

1. `f1e535eea3bddff9d313269f490ca064e80a7949` — `[PUBLIC-ONLY] Import sanitized public-safe baseline`
2. `3d07ef273adfb1d2bbfb6c71fd4b964426f341f8` — `[PUBLIC-ONLY] Add guarded public Android CI`

## Files modified after baseline

Public-only setup adds:

- `.github/workflows/public-ci.yml`
- `scripts/public_safe_guard.sh`
- `scripts/verify_public_safe_baseline.sh`
- `PUBLIC_REPO_POLICY.md`
- `TRANSFER_MANIFEST.md`

## Validation results

- Archive SHA-256: PASS.
- Sanitized baseline file-manifest verification: PASS (one-time provenance check).
- Recurring public-safe guard: PASS.
- Guard negative tests: PASS — rejects signing files, GitHub-token-shaped literals, and loss of `.publicdev`.
- Guard positive edit simulation: PASS — benign PRODUCT-style source/document changes are not blocked by baseline hash checks.
- Static forbidden-extension/path scan: PASS.
- Static literal secret-pattern scan: PASS.
- Production Firebase config: absent; inert public placeholder retained.
- Production signing configuration/material: absent.
- Debug public variant: `.publicdev` retained.
- Room schema: version 8; migrations 1→8 present; no destructive fallback found.
- Compile: PENDING GitHub Actions (local environment has no Android SDK/Gradle installation).
- Unit tests: PENDING GitHub Actions.
- Lint: PENDING GitHub Actions.
- Debug build: PENDING GitHub Actions.

## Remaining issues / notes

- The sanitized archive intentionally contains no Gradle Wrapper. Public CI installs Gradle 8.9 explicitly with `gradle/actions/setup-gradle`; this matches AGP 8.7.x requirements.
- Production Firebase/Auth/Cloud behavior is intentionally unavailable in this public mirror.
- Repository creation/publishing must preserve this fresh public history and must never import the private repository's Git history.

## Transfer instructions to CarManager Private

1. Do **not** merge the public repository or its `main` branch wholesale.
2. From the private repository, create a reviewed transfer branch from the intended private baseline.
3. Cherry-pick only the `[PRODUCT]` commits listed above, in the exact listed order.
4. Exclude all `[PUBLIC-ONLY]` commits unless a specific infrastructure change is separately reviewed and intentionally adopted.
5. Resolve conflicts manually with the private repository treated as authoritative for signing, production Firebase, private operational metadata, and release configuration.
6. Re-run private compile, unit tests, lint, database migration checks, debug/release build checks, and signing validation before merging.
7. Verify existing user-data migration paths remain non-destructive before release.

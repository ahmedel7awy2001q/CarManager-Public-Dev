# CarManager Transfer Manifest

Status: **ACTIVE — temporary public-development repository**

## Starting point

- Official public-safe archive: `CarManager_PUBLIC_SAFE_2026-09-12.zip`
- Archive SHA-256: `cd9bac91a1a15d2d821fc0727f1018aa8dc55e929f36a4fd9ad15cdaef12bea8`
- Private-source baseline reference recorded by the sanitized snapshot: `5fded81925d40a4819484aacdad4c90552220b19`
- Last verified product-source reference recorded by the sanitized snapshot: `1eeaab07d3d401577e4df26c82718e83c8702c03`
- Fresh public Git history baseline commit: `f1e535eea3bddff9d313269f490ca064e80a7949`

## Latest validated implementation/head commit

`604d4d96a106c7170f609cfb125f7685e4d6ce0f` — `[PUBLIC-ONLY] Add transfer manifest tracking`

This commit was validated by GitHub Actions run `34686941781`. A manifest cannot embed the SHA of the commit that is currently updating that same manifest without creating another commit, so the final bookkeeping/manifest-refresh commit may be newer while containing no PRODUCT source change.

## PRODUCT commits to transfer, in order

_None yet._

## PUBLIC-ONLY commits in the sanitized public history

1. `f1e535eea3bddff9d313269f490ca064e80a7949` — `[PUBLIC-ONLY] Import sanitized public-safe baseline`
2. `3d07ef273adfb1d2bbfb6c71fd4b964426f341f8` — `[PUBLIC-ONLY] Add guarded public Android CI`
3. `604d4d96a106c7170f609cfb125f7685e4d6ce0f` — `[PUBLIC-ONLY] Add transfer manifest tracking`

Any later manifest-only bookkeeping commits are also `[PUBLIC-ONLY]` and must not be transferred to production.

## Files modified after baseline

Public-only setup adds:

- `.github/workflows/public-ci.yml`
- `scripts/public_safe_guard.sh`
- `scripts/verify_public_safe_baseline.sh`
- `PUBLIC_REPO_POLICY.md`
- `TRANSFER_MANIFEST.md`

No PRODUCT file has been modified yet.

## Validation results

Validated GitHub Actions run: `34686941781` on head `604d4d96a106c7170f609cfb125f7685e4d6ce0f`.

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
- Public-safe guard in GitHub Actions: **PASS**.
- Compile/setup: **PASS** (JDK 17, Android SDK, Gradle 8.9 setup succeeded).
- Unit tests (`:app:testDebugUnitTest`): **PASS**.
- Lint (`:app:lintDebug`): **PASS**.
- Debug build (`:app:assembleDebug`): **PASS**.
- Reports artifact: `public-safe-reports` created successfully.
- APK artifact: `CarManager-publicdev-debug` created successfully.

## Remaining issues / notes

- The sanitized archive intentionally contains no Gradle Wrapper. Public CI installs Gradle 8.9 explicitly with `gradle/actions/setup-gradle`.
- Production Firebase/Auth/Cloud behavior is intentionally unavailable in this public mirror.
- Production signing is intentionally unavailable and must never be added to this public repository.
- Initial bootstrap push of the full staged history was blocked because the Actions `GITHUB_TOKEN` cannot create/update workflow files without workflow permission. This was resolved without adding credentials: the sanitized baseline ref was published first, then `main` was advanced through the already-verified public-safe commits using the authorized GitHub connector. No private Git history was introduced.
- Temporary import/bootstrap objects are PUBLIC-ONLY infrastructure and are not part of the production transfer set.

## Transfer instructions to CarManager Private

1. Do **not** merge the public repository or its `main` branch wholesale.
2. From the private repository, create a reviewed transfer branch from the intended private baseline.
3. Cherry-pick only the `[PRODUCT]` commits listed above, in the exact listed order.
4. Exclude all `[PUBLIC-ONLY]` commits unless a specific infrastructure change is separately reviewed and intentionally adopted.
5. Resolve conflicts manually with the private repository treated as authoritative for signing, production Firebase, private operational metadata, and release configuration.
6. Re-run private compile, unit tests, lint, database migration checks, debug/release build checks, and signing validation before merging.
7. Verify existing user-data migration paths remain non-destructive before release.

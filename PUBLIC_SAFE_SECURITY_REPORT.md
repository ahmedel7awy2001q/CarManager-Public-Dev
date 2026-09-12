# CarManager Public-Safe Security Review

Date: 2026-09-12

## Scope

Static security preparation of a public-development mirror from the current CarManager source snapshot. No GitHub repository was created and no GitHub Actions or Gradle build was run.

## Baseline

- Private baseline HEAD reference: `5fded81925d40a4819484aacdad4c90552220b19`
- Last verified product-source reference: `1eeaab07d3d401577e4df26c82718e83c8702c03`
- Source archive SHA-256 was verified before sanitization.

## Removed / neutralized

- Entire historical `.build/` recovery/archive tree, including ZIP/Base64 payloads.
- Existing `.github/` workflows and automation scripts, so publishing this snapshot cannot automatically start Actions.
- All signing directories and signing materials, including encrypted private-key payload and public signing certificate copies.
- Signing documentation and operational hand-off/build authorization files that expose unnecessary production metadata.
- Production Firebase/Google services configuration; replaced with an inert public-development placeholder.
- Production signing hooks from `app/build.gradle.kts`.
- Hard-coded developer WhatsApp number from the public mirror only.
- Historical recovery/patch payload directories not required by the direct Android source build.

## Public-development safeguards

- Debug package gets `applicationIdSuffix = ".publicdev"`.
- Debug version gets `versionNameSuffix = "-publicdev"`.
- Production signing configuration is absent.
- No existing GitHub workflow is present.
- `.gitignore` blocks common key, certificate, encrypted payload, credentials, secrets and local build artifacts.
- Placeholder Firebase config contains no production identifier or credential and is intended only to keep public development configuration isolated.

## Static scan results

The sanitized tree was scanned for:

- Private-key PEM headers.
- GitHub personal/access tokens.
- AWS access keys.
- Google API-key patterns.
- Slack tokens.
- Bearer/JWT literals.
- Literal password/secret/token/API-key assignments.
- Signing/key/certificate/encrypted/Base64/database/APK/AAB file types.
- Runtime database or user-data files.
- Exact production Firebase identifiers from the source snapshot.
- Exact production signing fingerprint metadata found in the source snapshot.
- Exact hard-coded private contact number found in the source snapshot.

Result: no literal secret-format credential and no exact original production identifier (other than the intentionally retained base Android application namespace/id) remained in the public-safe tree. Token/password assignment matches remaining in application code are runtime variables/function parameters, not embedded credential values.

## Important limitations

- This is a static source review, not proof against every possible secret format.
- The placeholder Firebase configuration is intentionally non-production; authentication/cloud features are not expected to work against the production backend in this mirror.
- The public mirror should never receive the production signing key, signing password, production `google-services.json`, production Maps key, runtime databases, exports/backups, or phone-local data.
- Before creating a public repository, perform one final review of the exact ZIP/hash below and use a fresh repository with no private Git history.
- Do not merge this sanitized baseline wholesale back into the private production repository. Transfer reviewed product commits only.

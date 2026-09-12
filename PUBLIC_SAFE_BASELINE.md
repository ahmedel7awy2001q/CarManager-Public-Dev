# CarManager Public-Safe Development Baseline

This directory is a sanitized development mirror prepared from the current CarManager source snapshot.

## Public-only safety changes

- No Git history is included.
- Existing GitHub Actions workflows are intentionally excluded. Add a new public-safe CI workflow only after review.
- Historical `.build` archives/Base64 payloads are excluded.
- All signing material and signing documentation are excluded.
- Production Firebase configuration is replaced by an inert placeholder config.
- Debug builds use `applicationIdSuffix = ".publicdev"` and `versionNameSuffix = "-publicdev"` so they cannot collide with the production app package.
- Production signing configuration is removed from this mirror.
- The embedded developer WhatsApp number is redacted in this mirror only.
- Runtime user databases, Firebase account data, and phone-local files are not part of this source snapshot.

## Baseline reference

Private-source baseline HEAD at preparation time:
`5fded81925d40a4819484aacdad4c90552220b19`

Last verified product-source commit:
`1eeaab07d3d401577e4df26c82718e83c8702c03`

Do not merge this mirror wholesale back into the private repository. Transfer reviewed product commits only; public-only safety changes must remain excluded from the private production branch unless explicitly desired.

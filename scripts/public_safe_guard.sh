#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  printf 'PUBLIC-SAFE GUARD FAILED: %b\n' "$1" >&2
  exit 1
}

printf '%s\n' '[guard] checking forbidden file types and paths...'
forbidden_files="$({
  find . -type f \( \
    -iname '*.jks' -o -iname '*.keystore' -o -iname '*.p12' -o -iname '*.pfx' -o \
    -iname '*.pem' -o -iname '*.key' -o -iname '*.crt' -o -iname '*.cer' -o \
    -iname '*.enc' -o -iname '*.b64' -o -iname '*.env' -o \
    -iname '*.db' -o -iname '*.sqlite' -o -iname '*.sqlite3' -o \
    -iname '*.aab' -o -iname '*.apk' \
  \) -print
  find . -type f \( -path './signing/*' -o -path './app/signing/*' -o -path './.build/*' \) -print
} | sort -u)"
[[ -z "$forbidden_files" ]] || fail "forbidden files found:\n$forbidden_files"

printf '%s\n' '[guard] checking public-development identity...'
grep -Fq 'applicationIdSuffix = ".publicdev"' app/build.gradle.kts \
  || fail 'debug applicationIdSuffix .publicdev is missing'
grep -Fq 'versionNameSuffix = "-publicdev"' app/build.gradle.kts \
  || fail 'debug versionNameSuffix -publicdev is missing'
if grep -Eq 'signingConfig|storeFile|storePassword|keyAlias|keyPassword' app/build.gradle.kts; then
  fail 'signing configuration is present in app/build.gradle.kts'
fi

printf '%s\n' '[guard] checking Firebase placeholder isolation...'
grep -Fq '"project_id": "carmanager-public-dev-placeholder"' app/google-services.json \
  || fail 'google-services.json is not the approved public placeholder'
grep -Fq '"package_name": "com.ahmed.carmanager.publicdev"' app/google-services.json \
  || fail 'google-services.json package is not the publicdev package'
grep -Fq 'PUBLIC_DEV_DUMMY_API_KEY' app/google-services.json \
  || fail 'dummy Firebase API key marker is missing'
grep -Fq 'MAPS_API_KEY=DEFAULT_API_KEY' local.defaults.properties \
  || fail 'local.defaults.properties does not contain the inert Maps placeholder'

printf '%s\n' '[guard] scanning literal secret formats...'
secret_regex='-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{25,}|xox[baprs]-[0-9A-Za-z-]{10,}|Bearer[[:space:]]+[A-Za-z0-9._-]{20,}'
if grep -RInE \
  --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle \
  --exclude='PUBLIC_SAFE_SECURITY_REPORT.md' --exclude='PUBLIC_SAFE_BASELINE.md' \
  --exclude='public_safe_guard.sh' \
  -- "$secret_regex" .; then
  fail 'literal secret-like credential pattern detected'
fi

printf '%s\n' '[guard] checking private contact redaction...'
grep -Fq 'val number = ""' app/src/main/java/com/ahmed/carmanager/ui/SettingsHubV090.kt \
  || fail 'public-safe developer contact redaction is not present'

printf '%s\n' 'PUBLIC-SAFE GUARD PASSED'

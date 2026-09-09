#!/bin/bash
# Regression check for the debug-only command-receiver boundary.
#
# Builds both variants and inspects the merged manifests in the actual APKs:
#   debug  -> CommandReceiver present, exported, DUMP-protected
#   release-> CommandReceiver absent
#
# Usage: ANDROID_HOME=/path/to/Android/Sdk ./verify-variants.sh
set -euo pipefail

cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must be set" >&2
  exit 2
fi

AAPT2="$(find "$SDK/build-tools" -name aapt2 -type f | sort | tail -1)"
if [ -z "$AAPT2" ]; then
  echo "aapt2 not found under $SDK/build-tools" >&2
  exit 2
fi

./gradlew --no-daemon :app:assembleDebug :app:assembleRelease >/dev/null

DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK=app/build/outputs/apk/release/app-release-unsigned.apk
DEBUG_MANIFEST=$(mktemp)
RELEASE_MANIFEST=$(mktemp)
trap 'rm -f "$DEBUG_MANIFEST" "$RELEASE_MANIFEST"' EXIT

"$AAPT2" dump xmltree --file AndroidManifest.xml "$DEBUG_APK" > "$DEBUG_MANIFEST"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$RELEASE_APK" > "$RELEASE_MANIFEST"

fail=0
if grep -q 'CommandReceiver' "$DEBUG_MANIFEST"; then
  echo "PASS debug APK declares CommandReceiver"
else
  echo "FAIL debug APK is missing CommandReceiver" >&2
  fail=1
fi
if grep -q 'android.permission.DUMP' "$DEBUG_MANIFEST"; then
  echo "PASS debug receiver keeps DUMP caller protection"
else
  echo "FAIL debug receiver lost DUMP caller protection" >&2
  fail=1
fi
if grep -q 'CommandReceiver' "$RELEASE_MANIFEST"; then
  echo "FAIL release APK still declares CommandReceiver" >&2
  fail=1
else
  echo "PASS release APK omits CommandReceiver"
fi

if [ "$fail" -ne 0 ]; then
  echo "variant boundary check FAILED" >&2
  exit 1
fi
echo "variant boundary check PASSED"

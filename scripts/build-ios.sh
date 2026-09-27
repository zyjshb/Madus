#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if ! command -v xcodebuild >/dev/null; then
  echo 'This script requires macOS with Xcode. On Windows use the iOS IPA GitHub Actions workflow.' >&2
  exit 1
fi
node scripts/generate-ios-project.mjs --check
swift test --package-path ios
xcodebuild -project ios/Madus.xcodeproj -scheme Madus -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath ios/build/DerivedData \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
# A new staging directory avoids stale Payload files and never deletes user folders.
mkdir -p ios/build
STAGING="$(mktemp -d "$ROOT/ios/build/package.XXXXXX")"
mkdir -p "$STAGING/Payload"
ditto ios/build/DerivedData/Build/Products/Release-iphoneos/Madus.app "$STAGING/Payload/Madus.app"
ditto -c -k --keepParent "$STAGING/Payload" ios/build/Madus-iOS-unsigned.ipa
shasum -a 256 ios/build/Madus-iOS-unsigned.ipa > ios/build/Madus-iOS-unsigned.ipa.sha256
echo 'Created ios/build/Madus-iOS-unsigned.ipa (requires signing before installation).'

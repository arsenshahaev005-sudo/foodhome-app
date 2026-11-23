#!/bin/bash
set -euo pipefail
umask 077

signing_dir="$RUNNER_TEMP/foodhome-testflight"
mkdir -p "$signing_dir"
xcodebuild -version
xcodebuild -help > "$signing_dir/xcode-help.txt" 2>&1
grep -q testFlightInternalTestingOnly "$signing_dir/xcode-help.txt" || {
  echo "::error::Selected Xcode does not support internal-only TestFlight exports."
  exit 1
}

printf '%s' "$BUILD_CERTIFICATE_BASE64" | base64 -D > "$signing_dir/distribution.p12"
printf '%s' "$BUILD_PROVISION_PROFILE_BASE64" | base64 -D > "$signing_dir/profile.mobileprovision"
unset BUILD_CERTIFICATE_BASE64 BUILD_PROVISION_PROFILE_BASE64
security cms -D -i "$signing_dir/profile.mobileprovision" -o "$signing_dir/profile.plist"

python3 - "$signing_dir" "$APP_BUNDLE_ID" <<'PY'
import hashlib
import plistlib
import sys
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

directory = Path(sys.argv[1])
bundle = sys.argv[2]
profile = plistlib.loads((directory / "profile.plist").read_bytes())
team, = profile["TeamIdentifier"]
certificate, = profile["DeveloperCertificates"]
entitlements = profile["Entitlements"]
if (entitlements.get("application-identifier") != f"{team}.{bundle}"
        or not entitlements.get("beta-reports-active")
        or entitlements.get("get-task-allow")
        or profile.get("ProvisionsAllDevices") or "ProvisionedDevices" in profile
        or profile["ExpirationDate"].replace(tzinfo=timezone.utc) <= datetime.now(timezone.utc)):
    sys.exit("Expected a current App Store distribution profile for this app.")
uuid = str(UUID(profile["UUID"])).upper()
# Xcode and security find-identity identify a certificate by its SHA-1 fingerprint.
options = {
    "method": "app-store-connect", "destination": "upload", "teamID": team,
    "signingStyle": "manual", "signingCertificate": hashlib.sha1(certificate).hexdigest().upper(),
    "provisioningProfiles": {bundle: uuid}, "manageAppVersionAndBuildNumber": False,
    "testFlightInternalTestingOnly": True,
}
(directory / "ExportOptions.plist").write_bytes(plistlib.dumps(options))
PY

team=$(plutil -extract teamID raw -o - "$signing_dir/ExportOptions.plist")
identity=$(plutil -extract signingCertificate raw -o - "$signing_dir/ExportOptions.plist")
profile_uuid=$(plutil -extract UUID raw -o - "$signing_dir/profile.plist")
echo "profile_uuid=$profile_uuid" >> "$GITHUB_OUTPUT"
profiles="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
mkdir -p "$profiles"
install -m 600 "$signing_dir/profile.mobileprovision" "$profiles/$profile_uuid.mobileprovision"

keychain="$signing_dir/distribution.keychain-db"
keychain_password=$(openssl rand -hex 32)
security create-keychain -p "$keychain_password" "$keychain"
security set-keychain-settings -lut 3600 "$keychain"
security unlock-keychain -p "$keychain_password" "$keychain"
security list-keychains -d user -s "$keychain" "$HOME/Library/Keychains/login.keychain-db"
security import "$signing_dir/distribution.p12" -f pkcs12 -k "$keychain" -P "$P12_PASSWORD" -T /usr/bin/codesign >/dev/null
unset P12_PASSWORD
rm -f "$signing_dir/distribution.p12"
security set-key-partition-list -S apple-tool:,apple: -s -k "$keychain_password" "$keychain" >/dev/null
unset keychain_password

# A Windows-exported PFX can omit Apple's public WWDR G3 intermediate.
if ! security find-identity -v -p codesigning "$keychain" | grep -F "$identity" >/dev/null; then
  curl --fail --silent --show-error --proto '=https' --max-time 30 \
    https://www.apple.com/certificateauthority/AppleWWDRCAG3.cer -o "$signing_dir/wwdr.cer"
  printf '%s  %s\n' dcf21878c77f4198e4b4614f03d696d89c66c66008d4244e1b99161aac91601f "$signing_dir/wwdr.cer" | shasum -a 256 -c - >/dev/null
  security import "$signing_dir/wwdr.cer" -k "$keychain" -t cert -f x509 >/dev/null
fi
security find-identity -v -p codesigning "$keychain" | grep -F "$identity" >/dev/null || {
  echo "::error::The imported signing identity does not match the profile's distribution certificate."
  exit 1
}

archive="$signing_dir/FoodHome.xcarchive"
build="$GITHUB_RUN_NUMBER.$GITHUB_RUN_ATTEMPT"
xcodebuild -project ios/FoodHomeApp.xcodeproj -scheme FoodHomeApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$archive" \
  CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM="$team" CODE_SIGN_IDENTITY="$identity" \
  PROVISIONING_PROFILE_SPECIFIER="$profile_uuid" CURRENT_PROJECT_VERSION="$build" archive

app="$archive/Products/Applications/FoodHomeApp.app"
codesign --verify --strict "$app"
[[ "$(plutil -extract CFBundleIdentifier raw -o - "$app/Info.plist")" == "$APP_BUNDLE_ID" ]]
[[ "$(plutil -extract CFBundleVersion raw -o - "$app/Info.plist")" == "$build" ]]
version=$(plutil -extract CFBundleShortVersionString raw -o - "$app/Info.plist")
printf 'version=%s\nbuild=%s\n' "$version" "$build" >> "$GITHUB_OUTPUT"
printf 'Archived %s (%s) from %s for internal TestFlight.\n' "$version" "$build" "$GITHUB_SHA" >> "$GITHUB_STEP_SUMMARY"

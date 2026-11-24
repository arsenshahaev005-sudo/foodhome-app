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
printf '%s' "$WIDGET_PROVISION_PROFILE_BASE64" | base64 -D > "$signing_dir/widget.mobileprovision"
unset BUILD_CERTIFICATE_BASE64 BUILD_PROVISION_PROFILE_BASE64 WIDGET_PROVISION_PROFILE_BASE64
for component in profile widget; do
  security cms -D -i "$signing_dir/$component.mobileprovision" -o "$signing_dir/$component.plist"
done
python3 scripts/ios_export_options.py "$signing_dir" "$APP_BUNDLE_ID" "$WIDGET_BUNDLE_ID" "$DISTRIBUTION_AUDIENCE"

team=$(plutil -extract teamID raw -o - "$signing_dir/ExportOptions.plist")
identity=$(plutil -extract signingCertificate raw -o - "$signing_dir/ExportOptions.plist")
profile_uuid=$(plutil -extract UUID raw -o - "$signing_dir/profile.plist")
widget_uuid=$(plutil -extract UUID raw -o - "$signing_dir/widget.plist")
printf 'profile_uuid=%s\nwidget_profile_uuid=%s\n' "$profile_uuid" "$widget_uuid" >> "$GITHUB_OUTPUT"
profiles="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
mkdir -p "$profiles"
install -m 600 "$signing_dir/profile.mobileprovision" "$profiles/$profile_uuid.mobileprovision"
install -m 600 "$signing_dir/widget.mobileprovision" "$profiles/$widget_uuid.mobileprovision"

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
  FOODHOME_APP_PROFILE="$profile_uuid" FOODHOME_WIDGET_PROFILE="$widget_uuid" \
  CURRENT_PROJECT_VERSION="$build" archive

app="$archive/Products/Applications/FoodHomeApp.app"
widget="$app/PlugIns/FoodHomeOrderStatus.appex"
codesign --verify --strict "$widget"
codesign --verify --strict "$app"
codesign -d --entitlements - "$app" > "$signing_dir/archived-entitlements.plist"
[[ "$(plutil -extract aps-environment raw -o - "$signing_dir/archived-entitlements.plist")" == production ]]
[[ "$(plutil -extract NSSupportsLiveActivities raw -o - "$app/Info.plist")" == true ]]
[[ "$(plutil -extract ITSAppUsesNonExemptEncryption raw -o - "$app/Info.plist")" == false ]]
[[ "$(plutil -extract CFBundleIdentifier raw -o - "$app/Info.plist")" == "$APP_BUNDLE_ID" ]]
[[ "$(plutil -extract CFBundleIdentifier raw -o - "$widget/Info.plist")" == "$WIDGET_BUNDLE_ID" ]]
[[ "$(plutil -extract NSExtension.NSExtensionPointIdentifier raw -o - "$widget/Info.plist")" == com.apple.widgetkit-extension ]]
[[ "$(plutil -extract CFBundleVersion raw -o - "$app/Info.plist")" == "$build" ]]
[[ "$(plutil -extract CFBundleVersion raw -o - "$widget/Info.plist")" == "$build" ]]
version=$(plutil -extract CFBundleShortVersionString raw -o - "$app/Info.plist")
[[ "$(plutil -extract CFBundleShortVersionString raw -o - "$widget/Info.plist")" == "$version" ]]
printf 'version=%s\nbuild=%s\n' "$version" "$build" >> "$GITHUB_OUTPUT"
printf 'Archived %s (%s) from %s for %s distribution.\n' "$version" "$build" "$GITHUB_SHA" "$DISTRIBUTION_AUDIENCE" >> "$GITHUB_STEP_SUMMARY"

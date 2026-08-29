import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const requireText = (content, expected, file) => {
  assert.ok(content.includes(expected), `${file} must contain ${expected}`);
};
const forbidText = (content, forbidden, file) => {
  assert.ok(!content.includes(forbidden), `${file} must not contain ${forbidden}`);
};

const manifest = JSON.parse(read("bridge-contract/manifest.json"));
assert.equal(manifest.contractVersion, "1.4.0");
assert.equal(manifest.bridgeMajor, 1);
assert.deepEqual(manifest.phase0Capabilities, []);
assert.deepEqual(manifest.compiledCapabilities, [
  "getNotificationStatus",
  "requestLocation",
  "requestNotificationPermission",
  "share",
]);
for (const forbidden of ["openPayment", "openAuth", "executeNativeMethod", "eval"]) {
  assert.equal(manifest.compiledCapabilities.includes(forbidden), false);
}

const androidManifestPath = "android/app/src/main/AndroidManifest.xml";
const androidManifest = read(androidManifestPath);
for (const permission of [
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.POST_NOTIFICATIONS",
]) {
  requireText(androidManifest, permission, androidManifestPath);
}
forbidText(androidManifest, "android.permission.CAMERA", androidManifestPath);
requireText(androidManifest, "androidx.core.content.FileProvider", androidManifestPath);
requireText(androidManifest, 'android:exported="false"', androidManifestPath);
requireText(androidManifest, '${applicationId}.fileprovider', androidManifestPath);
assert.equal(existsSync(resolve(root, "android/app/google-services.json")), false);

const androidWebViewPath =
  "android/app/src/main/java/market/foodhome/app/web/FoodHomeWebView.kt";
const androidWebView = read(androidWebViewPath);
requireText(androidWebView, "BridgeRequestValidator(manifest, environment.trustedOrigin)", androidWebViewPath);
requireText(androidWebView, "dispatcher.dispatch(result.request)", androidWebViewPath);
requireText(androidWebView, "fileChooserParams.isCaptureEnabled", androidWebViewPath);
forbidText(androidWebView, "addJavascriptInterface", androidWebViewPath);

const androidShellPath =
  "android/app/src/main/java/market/foodhome/app/ui/FoodHomeAppShell.kt";
const androidShell = read(androidShellPath);
requireText(androidShell, "ActivityResultContracts.PickVisualMedia", androidShellPath);
requireText(androidShell, "ActivityResultContracts.TakePicture", androidShellPath);
requireText(androidShell, "FileProvider.getUriForFile", androidShellPath);
requireText(androidShell, "showLocationConfirmation = true", androidShellPath);
requireText(androidShell, "showNotificationConfirmation = true", androidShellPath);
forbidText(androidShell, "Base64", androidShellPath);

const androidLocationPath =
  "android/app/src/main/java/market/foodhome/app/location/AndroidLocationProvider.kt";
const androidLocation = read(androidLocationPath);
requireText(androidLocation, "requestCurrentLocation", androidLocationPath);
requireText(androidLocation, '"TIMEOUT"', androidLocationPath);
forbidText(androidLocation, "requestLocationUpdates", androidLocationPath);

const androidPushPath =
  "android/app/src/main/java/market/foodhome/app/notifications/PushLifecyclePolicy.kt";
const androidPush = read(androidPushPath);
requireText(androidPush, "SensitivePushToken(<redacted>", androidPushPath);
requireText(androidPush, "DisabledPushTokenSink", androidPushPath);
forbidText(androidPush, "FirebaseMessagingService", androidPushPath);

const iosProjectPath = "ios/FoodHomeApp.xcodeproj/project.pbxproj";
const iosProject = read(iosProjectPath);
requireText(iosProject, "INFOPLIST_KEY_NSCameraUsageDescription", iosProjectPath);
requireText(iosProject, "INFOPLIST_KEY_NSLocationWhenInUseUsageDescription", iosProjectPath);
forbidText(iosProject, "CODE_SIGN_ENTITLEMENTS", iosProjectPath);
forbidText(iosProject, "com.apple.developer.associated-domains", iosProjectPath);

for (const file of [
  "IOSCapabilityCoordinator.swift",
  "IOSLocationProvider.swift",
  "IOSNotificationCoordinator.swift",
  "FoodHomeAppDelegate.swift",
  "TemporaryMediaStore.swift",
  "IOSMediaPickerCoordinator.swift",
]) {
  const references = iosProject.match(new RegExp(file.replace(".", "\\."), "g")) ?? [];
  assert.ok(references.length >= 3, `${file} must be referenced by the Xcode project`);
}

const iosWebViewPath = "ios/FoodHomeApp/WebView/WebViewStore.swift";
const iosWebView = read(iosWebViewPath);
requireText(iosWebView, "runOpenPanelWith parameters", iosWebViewPath);
assert.match(
  iosWebView,
  /@available\(iOS 18\.4, \*\)\s+func webView\(\s+_ webView: WKWebView,\s+runOpenPanelWith parameters: WKOpenPanelParameters,/,
  "iOS file-picker delegate must be availability-gated without raising the app deployment target",
);
requireText(iosWebView, "frame.isMainFrame", iosWebViewPath);
requireText(iosWebView, "mediaPickerCoordinator.present", iosWebViewPath);

const iosCapabilityPath =
  "ios/FoodHomeApp/Capabilities/IOSCapabilityCoordinator.swift";
const iosCapability = read(iosCapabilityPath);
requireText(iosCapability, "UIActivityViewController", iosCapabilityPath);
requireText(iosCapability, "locationProvider.requestCurrentLocation", iosCapabilityPath);
requireText(iosCapability, "notificationCoordinator.requestAuthorization", iosCapabilityPath);
requireText(iosCapability, "manifest.advertisedCapabilities", iosCapabilityPath);

const iosPushPath = "ios/FoodHomeApp/Notifications/FoodHomeAppDelegate.swift";
const iosPush = read(iosPushPath);
requireText(iosPush, "didRegisterForRemoteNotificationsWithDeviceToken", iosPushPath);
requireText(iosPush, "DisabledPushTokenSink", iosPushPath);
requireText(iosPush, "foodHomePushRoute", iosPushPath);
forbidText(iosPush, "print(", iosPushPath);

const iosMediaPath = "ios/FoodHomeApp/Media/IOSMediaPickerCoordinator.swift";
const iosMedia = read(iosMediaPath);
requireText(iosMedia, "PHPickerViewController", iosMediaPath);
requireText(iosMedia, "UIImagePickerController", iosMediaPath);
forbidText(iosMedia, "base64", iosMediaPath);

for (const forbiddenRoot of ["frontend", "backend", "server"]) {
  assert.equal(
    existsSync(resolve(root, forbiddenRoot)),
    false,
    `Phase 3 must not create a root ${forbiddenRoot}/ implementation`,
  );
}

const dependenciesPath = "docs/integration/food-home-required-changes.md";
const dependencies = read(dependenciesPath);
for (const task of [
  "FH-WEB-001",
  "FH-LINK-001",
  "FH-MEDIA-001",
  "FH-LOCATION-001",
  "FH-PUSH-001",
  "FH-PUSH-002",
  "FH-COMPAT-001",
]) {
  requireText(dependencies, task, dependenciesPath);
}

const repositoryText = [
  read(androidManifestPath),
  read(androidWebViewPath),
  read(androidShellPath),
  read(iosProjectPath),
  read(iosWebViewPath),
  read(iosCapabilityPath),
  read(iosPushPath),
].join("\n");
for (const forbidden of [
  "BEGIN PRIVATE KEY",
  "google-services.json\"",
  "FirebaseApp.configure()",
  "addJavascriptInterface",
]) {
  forbidText(repositoryText, forbidden, "Phase 3 implementation surface");
}

console.log("Phase 3 native capability invariants verified.");

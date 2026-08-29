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
const packageMetadata = JSON.parse(read("bridge-contract/package.json"));
const packageLock = JSON.parse(read("bridge-contract/package-lock.json"));

assert.equal(manifest.contractVersion, "1.4.0");
assert.equal(packageMetadata.version, manifest.contractVersion);
assert.equal(packageLock.version, manifest.contractVersion);
assert.equal(manifest.bridgeMajor, 1);
assert.deepEqual(manifest.supportedVersions, [1]);
assert.deepEqual(manifest.nativeMode, {
  protocol: "foodhome.native-mode",
  version: 1,
  globalObjectName: "FoodHomeNative",
  userAgentProduct: "FoodHomeNative/1",
  securityBoundary: false,
  documentStart: {
    trustedOriginOnly: true,
    mainFrameOnly: true,
  },
});

const androidBootstrapPath =
  "android/app/src/main/java/market/foodhome/app/bridge/NativeModeBootstrap.kt";
const androidBootstrap = read(androidBootstrapPath);
requireText(androidBootstrap, "window !== window.top", androidBootstrapPath);
requireText(androidBootstrap, "window.location.origin !==", androidBootstrapPath);
requireText(androidBootstrap, "Object.freeze", androidBootstrapPath);
forbidText(androidBootstrap, "addJavascriptInterface", androidBootstrapPath);
forbidText(androidBootstrap, "*://", androidBootstrapPath);

const androidWebViewPath =
  "android/app/src/main/java/market/foodhome/app/web/FoodHomeWebView.kt";
const androidWebView = read(androidWebViewPath);
requireText(androidWebView, "WebViewFeature.DOCUMENT_START_SCRIPT", androidWebViewPath);
requireText(androidWebView, "WebViewCompat.addDocumentStartJavaScript", androidWebViewPath);
requireText(
  androidWebView,
  "setOf(environment.trustedOrigin.toASCIIString())",
  androidWebViewPath,
);
assert.ok(
  androidWebView.indexOf("WebViewFeature.DOCUMENT_START_SCRIPT") <
    androidWebView.indexOf("WebViewCompat.addDocumentStartJavaScript"),
  "Android must feature-detect document-start injection before registration",
);

const iosManifestPath = "ios/FoodHomeApp/Bridge/BridgeManifest.swift";
const iosManifest = read(iosManifestPath);
requireText(iosManifest, "enum NativeModeBootstrap", iosManifestPath);
requireText(iosManifest, "window !== window.top", iosManifestPath);
requireText(iosManifest, "window.location.origin !==", iosManifestPath);
requireText(iosManifest, "Object.freeze", iosManifestPath);
forbidText(iosManifest, "*://", iosManifestPath);

const iosWebViewPath = "ios/FoodHomeApp/WebView/WebViewStore.swift";
const iosWebView = read(iosWebViewPath);
requireText(iosWebView, "applicationNameForUserAgent", iosWebViewPath);
requireText(iosWebView, "injectionTime: .atDocumentStart", iosWebViewPath);
requireText(iosWebView, "forMainFrameOnly: true", iosWebViewPath);
assert.ok(
  iosWebView.indexOf("configuration.userContentController.addUserScript") <
    iosWebView.indexOf("let webView = WKWebView"),
  "iOS must install the native-mode marker before constructing WKWebView",
);

const androidConfigPath =
  "android/app/src/main/java/market/foodhome/app/config/RemoteConfigPolicy.kt";
const androidConfig = read(androidConfigPath);
requireText(androidConfig, "LAST_KNOWN_GOOD", androidConfigPath);
requireText(androidConfig, "cachedSnapshot", androidConfigPath);
requireText(androidConfig, "builtInCapabilities", androidConfigPath);
requireText(androidConfig, ".intersect(providerPolicyCapabilities)", androidConfigPath);
requireText(androidConfig, ".intersect(snapshot.requestedCapabilities)", androidConfigPath);

const iosConfigPath = "ios/FoodHomeApp/Configuration/RemoteConfigPolicy.swift";
const iosConfig = read(iosConfigPath);
requireText(iosConfig, "lastKnownGood", iosConfigPath);
requireText(iosConfig, "cachedSnapshot", iosConfigPath);
requireText(iosConfig, "builtInCapabilities", iosConfigPath);
requireText(iosConfig, ".intersection(providerPolicyCapabilities)", iosConfigPath);
requireText(iosConfig, ".intersection(snapshot.requestedCapabilities)", iosConfigPath);

for (const forbiddenRoot of ["frontend", "backend", "server"]) {
  assert.equal(
    existsSync(resolve(root, forbiddenRoot)),
    false,
    `Phase 2 must not create a root ${forbiddenRoot}/ implementation`,
  );
}

const handoffPath = "docs/integration/food-home-required-changes.md";
const handoff = read(handoffPath);
for (const task of [
  "FH-WEB-001",
  "FH-WEB-002",
  "FH-LINK-001",
  "FH-CONFIG-001",
  "FH-PUSH-001",
  "FH-PUSH-002",
  "FH-AUTH-001",
  "FH-PAY-001",
  "FH-SEC-001",
  "FH-COMPAT-001",
]) {
  requireText(handoff, task, handoffPath);
}

console.log("Phase 2 cross-repository integration invariants verified.");

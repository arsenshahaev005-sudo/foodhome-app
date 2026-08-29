import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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
assert.equal(manifest.trustedProductionOrigin, "https://foodhome.market");
assert.deepEqual(manifest.phase0Capabilities, []);

const androidManifestPath = "android/app/src/main/AndroidManifest.xml";
const androidManifest = read(androidManifestPath);
requireText(androidManifest, 'android:usesCleartextTraffic="false"', androidManifestPath);
requireText(androidManifest, 'android:allowBackup="false"', androidManifestPath);
requireText(androidManifest, 'android:windowSoftInputMode="adjustResize"', androidManifestPath);

const networkConfigPath = "android/app/src/main/res/xml/network_security_config.xml";
const networkConfig = read(networkConfigPath);
requireText(networkConfig, 'cleartextTrafficPermitted="false"', networkConfigPath);
requireText(networkConfig, '<certificates src="system" />', networkConfigPath);
forbidText(networkConfig, 'src="user"', networkConfigPath);

const androidEnvironmentPath =
  "android/app/src/main/java/market/foodhome/app/config/AppEnvironment.kt";
const androidEnvironment = read(androidEnvironmentPath);
requireText(androidEnvironment, 'URI("https://foodhome.market")', androidEnvironmentPath);
requireText(androidEnvironment, "if (!isDebug", androidEnvironmentPath);

const androidWebViewPath =
  "android/app/src/main/java/market/foodhome/app/web/FoodHomeWebView.kt";
const androidWebView = read(androidWebViewPath);
requireText(
  androidWebView,
  "WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)",
  androidWebViewPath,
);
requireText(androidWebView, "WebViewFeature.WEB_MESSAGE_LISTENER", androidWebViewPath);
requireText(androidWebView, "settings.allowFileAccess = false", androidWebViewPath);
requireText(androidWebView, "settings.allowContentAccess = false", androidWebViewPath);
requireText(androidWebView, "MIXED_CONTENT_NEVER_ALLOW", androidWebViewPath);
requireText(androidWebView, "handler.cancel()", androidWebViewPath);
forbidText(androidWebView, "addJavascriptInterface", androidWebViewPath);

const iosEnvironmentPath = "ios/FoodHomeApp/Configuration/AppEnvironment.swift";
const iosEnvironment = read(iosEnvironmentPath);
requireText(iosEnvironment, 'URL(string: "https://foodhome.market")', iosEnvironmentPath);
requireText(iosEnvironment, "#if DEBUG", iosEnvironmentPath);

const iosWebViewPath = "ios/FoodHomeApp/WebView/WebViewStore.swift";
const iosWebView = read(iosWebViewPath);
requireText(iosWebView, "configuration.websiteDataStore = .default()", iosWebViewPath);
requireText(iosWebView, "webView.isInspectable = false", iosWebViewPath);
requireText(iosWebView, ".performDefaultHandling", iosWebViewPath);
forbidText(iosWebView, ".useCredential", iosWebViewPath);

const iosBridgePath = "ios/FoodHomeApp/Bridge/BridgeMessageHandler.swift";
const iosBridge = read(iosBridgePath);
requireText(iosBridge, "WKScriptMessageHandlerWithReply", iosBridgePath);
forbidText(iosBridge, "evaluateJavaScript(message", iosBridgePath);

console.log("Secure shell invariants verified.");

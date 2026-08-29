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

const manifestPath = "bridge-contract/manifest.json";
const manifest = JSON.parse(read(manifestPath));
assert.equal(manifest.contractVersion, "1.4.0");
assert.equal(manifest.bridgeMajor, 1);
assert.ok(manifest.builtInCapabilities.includes("openPayment"));
assert.equal(manifest.advertisedCapabilities.includes("openPayment"), false);
assert.equal(manifest.compiledCapabilities.includes("openPayment"), false);
assert.deepEqual(manifest.nativeEvents, {
  transport: "dom-event",
  eventName: "foodhome:native-event",
});
for (const method of ["openPayment", "ackNativeEvent", "clearPaymentRecovery"]) {
  assert.ok(manifest.methods.includes(method), `${method} must be in the bridge method allowlist`);
}
assert.equal(manifest.conditionalMethods.openAuth.includes("disabled"), true);

const androidPolicyPath =
  "android/app/src/main/java/market/foodhome/app/payments/PaymentLaunchPolicy.kt";
const androidPolicy = read(androidPolicyPath);
requireText(androidPolicy, "fun production(): PaymentLaunchPolicy = PaymentLaunchPolicy(emptyList())", androidPolicyPath);
requireText(androidPolicy, "uri.port !in setOf(-1, 443)", androidPolicyPath);
requireText(androidPolicy, "normalizedHost == candidate.exactHost.lowercase()", androidPolicyPath);
forbidText(androidPolicy, "URLDecoder", androidPolicyPath);

const androidStorePath =
  "android/app/src/main/java/market/foodhome/app/payments/PaymentRecoveryStore.kt";
const androidStore = read(androidStorePath);
requireText(androidStore, ".commit()", androidStorePath);
requireText(androidStore, "Context.MODE_PRIVATE", androidStorePath);

const androidModelsPath =
  "android/app/src/main/java/market/foodhome/app/payments/PaymentModels.kt";
const androidModels = read(androidModelsPath);
const androidSnapshot = androidModels.slice(
  androidModels.indexOf("data class PaymentRecoverySnapshot"),
  androidModels.indexOf("data class OpenPaymentRequest"),
);
for (const forbidden of ["url", "query", "amount", "currency", "orderId", "userId", "providerId", "token"] ) {
  assert.equal(
    androidSnapshot.toLowerCase().includes(forbidden.toLowerCase()),
    false,
    `Android recovery snapshot must not persist ${forbidden}`,
  );
}

const androidCoordinatorPath =
  "android/app/src/main/java/market/foodhome/app/payments/PaymentCoordinator.kt";
const androidCoordinator = read(androidCoordinatorPath);
requireText(androidCoordinator, "minOf(request.serverExpiresAtEpochMillis, localExpiry)", androidCoordinatorPath);
requireText(androidCoordinator, "if (!store.write(prepared))", androidCoordinatorPath);
requireText(androidCoordinator, "snapshot.acknowledgedEventId == eventId", androidCoordinatorPath);
const androidAck = androidCoordinator.slice(
  androidCoordinator.indexOf("fun acknowledge"),
  androidCoordinator.indexOf("fun clear"),
);
requireText(androidAck, "store.write(acknowledged)", androidCoordinatorPath);
forbidText(androidAck, "store.clear", androidCoordinatorPath);

const androidQueuePath =
  "android/app/src/main/java/market/foodhome/app/bridge/NativeEventQueue.kt";
const androidQueue = read(androidQueuePath);
requireText(androidQueue, "Returns the same pending event until", androidQueuePath);
requireText(read(manifestPath), "foodhome:native-event", manifestPath);

const androidLauncherPath =
  "android/app/src/main/java/market/foodhome/app/payments/AndroidPaymentLauncher.kt";
const androidLauncher = read(androidLauncherPath);
forbidText(androidLauncher, "Intent.parseUri", androidLauncherPath);
forbidText(androidLauncher, "setPackage", androidLauncherPath);

const androidWebViewPath =
  "android/app/src/main/java/market/foodhome/app/web/FoodHomeWebView.kt";
const androidWebView = read(androidWebViewPath);
requireText(androidWebView, "is NavigationDecision.External ->", androidWebViewPath);
requireText(androidWebView, "onOpenExternal", androidWebViewPath);
requireText(androidWebView, "JavaScript execution is not an ACK", androidWebViewPath);
forbidText(androidWebView, "addJavascriptInterface", androidWebViewPath);

const iosPolicyPath = "ios/FoodHomeApp/Payments/PaymentLaunchPolicy.swift";
const iosPolicy = read(iosPolicyPath);
requireText(iosPolicy, "static let production = PaymentLaunchPolicy(rules: [])", iosPolicyPath);
requireText(iosPolicy, "components.port == nil || components.port == 443", iosPolicyPath);
requireText(iosPolicy, "host == $0.exactHost.lowercased()", iosPolicyPath);
forbidText(iosPolicy, "removingPercentEncoding", iosPolicyPath);

const iosStorePath = "ios/FoodHomeApp/Payments/PaymentRecoveryStore.swift";
const iosStore = read(iosStorePath);
requireText(iosStore, ".atomic", iosStorePath);
requireText(iosStore, "isExcludedFromBackup = true", iosStorePath);

const iosModelsPath = "ios/FoodHomeApp/Payments/PaymentModels.swift";
const iosModels = read(iosModelsPath);
const iosSnapshot = iosModels.slice(
  iosModels.indexOf("struct PaymentRecoverySnapshot"),
  iosModels.indexOf("struct OpenPaymentRequest"),
);
for (const forbidden of ["url", "query", "amount", "currency", "orderID", "userID", "providerID", "token"] ) {
  assert.equal(
    iosSnapshot.toLowerCase().includes(forbidden.toLowerCase()),
    false,
    `iOS recovery snapshot must not persist ${forbidden}`,
  );
}

const iosCoordinatorPath = "ios/FoodHomeApp/Payments/PaymentCoordinator.swift";
const iosCoordinator = read(iosCoordinatorPath);
requireText(iosCoordinator, "min(request.serverExpiresAtEpochMilliseconds, cappedLocalExpiry)", iosCoordinatorPath);
requireText(iosCoordinator, "guard store.write(prepared)", iosCoordinatorPath);
const iosAck = iosCoordinator.slice(
  iosCoordinator.indexOf("func acknowledge"),
  iosCoordinator.indexOf("func clear"),
);
requireText(iosAck, "store.write(acknowledged)", iosCoordinatorPath);
forbidText(iosAck, "store.clear", iosCoordinatorPath);

const iosWebViewPath = "ios/FoodHomeApp/WebView/WebViewStore.swift";
const iosWebView = read(iosWebViewPath);
requireText(iosWebView, "case let .external(externalURL)", iosWebViewPath);
requireText(iosWebView, "openExternal(externalURL)", iosWebViewPath);
requireText(iosWebView, "only ackNativeEvent removes it", iosWebViewPath);

const iosProjectPath = "ios/FoodHomeApp.xcodeproj/project.pbxproj";
const iosProject = read(iosProjectPath);
forbidText(iosProject, "com.apple.developer.associated-domains", iosProjectPath);
forbidText(iosProject, "CODE_SIGN_ENTITLEMENTS", iosProjectPath);
for (const file of [
  "PaymentModels.swift",
  "PaymentLaunchPolicy.swift",
  "PaymentRecoveryStore.swift",
  "PaymentCoordinator.swift",
  "IOSPaymentLauncher.swift",
  "IOSPaymentReturnRouter.swift",
  "NativeEventQueue.swift",
  "PaymentLaunchPolicyTests.swift",
  "PaymentRecoveryStoreTests.swift",
  "PaymentCoordinatorTests.swift",
  "IOSPaymentReturnRouterTests.swift",
  "NativeEventQueueTests.swift",
]) {
  const references = iosProject.match(new RegExp(file.replace(".", "\\."), "g")) ?? [];
  assert.ok(references.length >= 3, `${file} must be referenced by the Xcode project`);
}

const nativePaymentSurface = [
  androidPolicy,
  androidStore,
  androidModels,
  androidCoordinator,
  androidLauncher,
  iosPolicy,
  iosStore,
  iosModels,
  iosCoordinator,
  read("ios/FoodHomeApp/Payments/IOSPaymentLauncher.swift"),
].join("\n");
for (const forbidden of [
  "BEGIN PRIVATE KEY",
  "Authorization: Bearer",
  "HttpURLConnection",
  "OkHttpClient",
  "URLSession.shared.dataTask",
  "pre.tochka.com",
]) {
  forbidText(nativePaymentSurface, forbidden, "native payment surface");
}

for (const forbiddenRoot of ["frontend", "backend", "server"]) {
  assert.equal(existsSync(resolve(root, forbiddenRoot)), false);
}

console.log("Phase 4 payment and recovery invariants verified.");

import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const requireFile = (path) => {
  assert.ok(existsSync(resolve(root, path)), `${path} must exist`);
  return read(path);
};
const requireText = (content, expected, file) => {
  assert.ok(content.includes(expected), `${file} must contain ${expected}`);
};
const forbidText = (content, forbidden, file) => {
  assert.ok(!content.includes(forbidden), `${file} must not contain ${forbidden}`);
};
const sorted = (values) => [...values].sort();

const requiredFiles = [
  "bridge-contract/tests/adversarial.test.mjs",
  "bridge-contract/fixtures/invalid/request-control-character-id.json",
  "bridge-contract/fixtures/invalid/request-payload-too-deep.json",
  "bridge-contract/fixtures/invalid/request-prototype-key.json",
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetryEvent.kt",
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetrySanitizer.kt",
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetrySink.kt",
  "android/app/src/test/java/market/foodhome/app/telemetry/TelemetrySanitizerTest.kt",
  "ios/FoodHomeApp/Telemetry/TelemetryEvent.swift",
  "ios/FoodHomeApp/Telemetry/TelemetrySanitizer.swift",
  "ios/FoodHomeApp/Telemetry/TelemetrySink.swift",
  "ios/FoodHomeAppTests/TelemetrySanitizerTests.swift",
  "docs/security/mobile-threat-model.md",
  "docs/privacy/mobile-data-inventory.md",
  "docs/store/store-readiness.md",
  "docs/store/metadata-template.md",
  "docs/store/demo-account-requirements.md",
  "docs/runbooks/phase-5-preflight.md",
  "docs/runbooks/real-device-qa.md",
  "docs/runbooks/testflight-internal-testing.md",
  "docs/runbooks/store-submission.md",
  "docs/runbooks/controlled-rollout.md",
  "docs/runbooks/mobile-monitoring-and-incidents.md",
  "docs/runbooks/rollback.md",
  "docs/reports/phase-5-verification.md",
];
for (const file of requiredFiles) requireFile(file);

const manifestPath = "bridge-contract/manifest.json";
const manifest = JSON.parse(read(manifestPath));
assert.equal(manifest.contractVersion, "1.4.0");
assert.equal(manifest.bridgeMajor, 1);
assert.equal(manifest.limits.maxJsonDepth, 12);
assert.equal(manifest.limits.maxJsonNodes, 512);
assert.ok(manifest.builtInCapabilities.includes("openPayment"));
assert.equal(manifest.advertisedCapabilities.includes("openPayment"), false);
assert.equal(manifest.compiledCapabilities.includes("openPayment"), false);
assert.deepEqual(manifest.rateLimits, {
  requestLocation: { maxRequests: 3, windowSeconds: 60 },
  openPayment: { maxRequests: 3, windowSeconds: 60 },
  requestNotificationPermission: { maxRequests: 1, windowSeconds: 30 },
});

const adversarialPath = "bridge-contract/tests/adversarial.test.mjs";
const adversarial = read(adversarialPath);
for (const expected of [
  "seeded(0x46484d35)",
  "index < 500",
  "assert.equal(rejected, 500)",
  "maxJsonDepth",
  "maxJsonNodes",
  "request-prototype-key.json",
]) {
  requireText(adversarial, expected, adversarialPath);
}

const expectedEvents = sorted([
  "shell.launch",
  "webview.load.failed",
  "webview.renderer.terminated",
  "bridge.request.failed",
  "bridge.method.unsupported",
  "bridge.version.incompatible",
  "deeplink.open.failed",
  "push.open.failed",
  "payment.return.failed",
  "mobile.config.failed",
]);
const expectedAttributes = sorted([
  "platform",
  "appVersion",
  "bridgeVersion",
  "webViewVersion",
  "routeTemplate",
  "errorCode",
  "correlationId",
  "networkClass",
  "durationBucket",
]);

const androidEventPath =
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetryEvent.kt";
const androidEvent = read(androidEventPath);
const androidEventSection = androidEvent.slice(
  androidEvent.indexOf("enum class TelemetryEventName"),
  androidEvent.indexOf("enum class TelemetryAttributeKey"),
);
const androidAttributeSection = androidEvent.slice(
  androidEvent.indexOf("enum class TelemetryAttributeKey"),
  androidEvent.indexOf("data class TelemetryEvent"),
);
const androidEvents = sorted(
  [...androidEventSection.matchAll(/\w+\("([a-z.]+)"\)/g)].map((match) => match[1]),
);
const androidAttributes = sorted(
  [...androidAttributeSection.matchAll(/\w+\("([A-Za-z]+)"\)/g)].map((match) => match[1]),
);

const iosEventPath = "ios/FoodHomeApp/Telemetry/TelemetryEvent.swift";
const iosEvent = read(iosEventPath);
const iosEventSection = iosEvent.slice(
  iosEvent.indexOf("enum TelemetryEventName"),
  iosEvent.indexOf("enum TelemetryAttributeKey"),
);
const iosAttributeSection = iosEvent.slice(
  iosEvent.indexOf("enum TelemetryAttributeKey"),
  iosEvent.indexOf("struct TelemetryEvent"),
);
const iosEvents = sorted(
  [...iosEventSection.matchAll(/case\s+\w+\s*=\s*"([a-z.]+)"/g)].map((match) => match[1]),
);
const iosAttributes = sorted(
  [...iosAttributeSection.matchAll(/case\s+(\w+)/g)].map((match) => match[1]),
);
assert.deepEqual(androidEvents, expectedEvents, "Android telemetry event allowlist drifted");
assert.deepEqual(iosEvents, expectedEvents, "iOS telemetry event allowlist drifted");
assert.deepEqual(androidAttributes, expectedAttributes, "Android telemetry attributes drifted");
assert.deepEqual(iosAttributes, expectedAttributes, "iOS telemetry attributes drifted");

const androidTelemetryPaths = [
  androidEventPath,
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetrySanitizer.kt",
  "android/app/src/main/java/market/foodhome/app/telemetry/TelemetrySink.kt",
];
const iosTelemetryPaths = [
  iosEventPath,
  "ios/FoodHomeApp/Telemetry/TelemetrySanitizer.swift",
  "ios/FoodHomeApp/Telemetry/TelemetrySink.swift",
];
const telemetrySurface = [...androidTelemetryPaths, ...iosTelemetryPaths]
  .map(read)
  .join("\n");
for (const forbidden of [
  "OkHttp",
  "HttpURLConnection",
  "URLSession.shared",
  "FileManager",
  "FirebaseAnalytics",
  "Crashlytics",
  "Sentry",
  "Datadog",
  "NewRelic",
  "AppCenter",
  "AdvertisingId",
  "identifierForVendor",
  "print(",
  "println(",
  "NSLog",
  "os_log",
  "Log.",
]) {
  forbidText(telemetrySurface, forbidden, "native telemetry surface");
}
for (const expected of ["NoOpTelemetrySink", "routeTemplate", "maximumURLBytes"]) {
  requireText(telemetrySurface, expected, "native telemetry surface");
}

const androidCoordinatorPath =
  "android/app/src/main/java/market/foodhome/app/capabilities/AndroidCapabilityCoordinator.kt";
const androidCoordinator = read(androidCoordinatorPath);
requireText(androidCoordinator, "manifest.rateLimits[method]", androidCoordinatorPath);
requireText(androidCoordinator, '"CAPABILITY_UNAVAILABLE"', androidCoordinatorPath);
forbidText(androidCoordinator, 'enforceRateLimit("openPayment", 3', androidCoordinatorPath);
forbidText(androidCoordinator, 'enforceRateLimit("requestLocation", 3', androidCoordinatorPath);

const iosCoordinatorPath = "ios/FoodHomeApp/Capabilities/IOSCapabilityCoordinator.swift";
const iosCoordinator = read(iosCoordinatorPath);
requireText(iosCoordinator, "manifest.rateLimits[method]", iosCoordinatorPath);
requireText(iosCoordinator, '"CAPABILITY_UNAVAILABLE"', iosCoordinatorPath);
forbidText(iosCoordinator, 'enforceRateLimit(method: "openPayment", maxRequests:', iosCoordinatorPath);
forbidText(iosCoordinator, 'enforceRateLimit(method: "requestLocation", maxRequests:', iosCoordinatorPath);

const androidPaymentPolicyPath =
  "android/app/src/main/java/market/foodhome/app/payments/PaymentLaunchPolicy.kt";
const iosPaymentPolicyPath = "ios/FoodHomeApp/Payments/PaymentLaunchPolicy.swift";
requireText(
  read(androidPaymentPolicyPath),
  "fun production(): PaymentLaunchPolicy = PaymentLaunchPolicy(emptyList())",
  androidPaymentPolicyPath,
);
requireText(
  read(iosPaymentPolicyPath),
  "static let production = PaymentLaunchPolicy(rules: [])",
  iosPaymentPolicyPath,
);

for (const [file, markers] of [
  [
    "android/app/src/test/java/market/foodhome/app/navigation/NavigationPolicyTest.kt",
    ["encoded nested destination", "trailing-dot and unicode lookalikes"],
  ],
  [
    "android/app/src/test/java/market/foodhome/app/payments/PaymentLaunchPolicyTest.kt",
    ["malformed encoded and overlong", "ambiguous host path and unicode"],
  ],
  [
    "ios/FoodHomeAppTests/NavigationPolicyTests.swift",
    ["nestedURL", "foodhome.market."],
  ],
  [
    "ios/FoodHomeAppTests/PaymentLaunchPolicyTests.swift",
    ["%252F", "pay.example.invalid."],
  ],
]) {
  const content = read(file);
  for (const marker of markers) requireText(content, marker, file);
}

const iosProjectPath = "ios/FoodHomeApp.xcodeproj/project.pbxproj";
const iosProject = read(iosProjectPath);
for (const file of [
  "TelemetryEvent.swift",
  "TelemetrySanitizer.swift",
  "TelemetrySink.swift",
  "TelemetrySanitizerTests.swift",
]) {
  const references = iosProject.match(new RegExp(file.replace(".", "\\."), "g")) ?? [];
  assert.ok(references.length >= 3, `${file} must be referenced by the Xcode project`);
}

const dependencyPath = "docs/integration/food-home-required-changes.md";
const dependencies = read(dependencyPath);
for (const task of [
  "FH-SEC-001",
  "FH-PRIV-001",
  "FH-COMPAT-001",
  "FH-OBS-001",
  "FH-STORE-001",
  "FH-SIGNAL-001",
]) {
  requireText(dependencies, task, dependencyPath);
}
requireText(dependencies, "artifact `1.4.0`", dependencyPath);

const threatModel = read("docs/security/mobile-threat-model.md");
for (const required of ["First-party XSS", "Forged redirect", "Supply-chain", "Telemetry"]) {
  requireText(threatModel, required, "docs/security/mobile-threat-model.md");
}
const privacyInventory = read("docs/privacy/mobile-data-inventory.md");
for (const required of ["Web session cookies", "Opaque payment recovery context", "Telemetry allowlist"]) {
  requireText(privacyInventory, required, "docs/privacy/mobile-data-inventory.md");
}
const deviceRunbook = read("docs/runbooks/real-device-qa.md");
const scenarios = deviceRunbook.match(/^\d+\. \*\*/gm) ?? [];
assert.equal(scenarios.length, 25, "real-device QA must retain all 25 acceptance scenarios");
const verificationReport = read("docs/reports/phase-5-verification.md");
for (let section = 1; section <= 10; section += 1) {
  requireText(verificationReport, `## ${section}.`, "docs/reports/phase-5-verification.md");
}
requireText(verificationReport, "Do not start Phase 6", "docs/reports/phase-5-verification.md");

const phaseFiveDocs = [
  threatModel,
  privacyInventory,
  read("docs/store/store-readiness.md"),
  read("docs/runbooks/phase-5-preflight.md"),
].join("\n");
forbidText(phaseFiveDocs, "`PARTIAL`", "Phase 5 evidence documents");

const dependencySurface = [
  read("android/app/build.gradle.kts"),
  read("android/gradle/libs.versions.toml"),
  iosProject,
].join("\n");
for (const vendor of ["sentry", "crashlytics", "firebase-analytics", "datadog", "newrelic", "appcenter"]) {
  forbidText(dependencySurface.toLowerCase(), vendor, "native dependency surface");
}

for (const forbiddenRoot of ["frontend", "backend", "server"]) {
  assert.equal(existsSync(resolve(root, forbiddenRoot)), false);
}

console.log("Phase 5 hardening, privacy, and store-readiness invariants verified.");

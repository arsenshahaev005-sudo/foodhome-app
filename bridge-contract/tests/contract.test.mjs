import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

import {
  SAFE_REMOTE_CONFIG,
  TerminalResponseGuard,
  isJsonStructureSafe,
  isMessageWithinLimit,
  resolveRemoteConfig,
} from "../lib/policy.mjs";

const contractRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

async function loadJson(relativePath) {
  return JSON.parse(await readFile(path.join(contractRoot, relativePath), "utf8"));
}

const manifest = await loadJson("manifest.json");
const packageMetadata = await loadJson("package.json");
const packageLock = await loadJson("package-lock.json");
const schemas = {
  request: await loadJson("schemas/request.schema.json"),
  response: await loadJson("schemas/response.schema.json"),
  handshake: await loadJson("schemas/handshake.schema.json"),
  "native-event": await loadJson("schemas/native-event.schema.json"),
  "remote-config": await loadJson("schemas/remote-config.schema.json"),
};

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const validators = Object.fromEntries(
  Object.entries(schemas).map(([name, schema]) => [name, ajv.compile(schema)]),
);

function schemaNameForFixture(filename) {
  if (filename.startsWith("native-event")) return "native-event";
  if (filename.startsWith("remote-config")) return "remote-config";
  return path.basename(filename, ".json").split("-")[0];
}

test("all valid fixtures conform to their schemas", async () => {
  const directory = path.join(contractRoot, "fixtures", "valid");
  for (const filename of (await readdir(directory)).sort()) {
    const schemaName = schemaNameForFixture(filename);
    const fixture = await loadJson(path.join("fixtures", "valid", filename));
    const valid = validators[schemaName](fixture);
    assert.equal(valid, true, `${filename}: ${ajv.errorsText(validators[schemaName].errors)}`);
  }
});

test("schema-invalid fixtures fail closed", async () => {
  const directory = path.join(contractRoot, "fixtures", "invalid");
  const filenames = (await readdir(directory))
    .filter((filename) => ![
      "remote-config-expired.json",
      "request-payload-too-deep.json",
      "request-prototype-key.json",
    ].includes(filename))
    .sort();

  for (const filename of filenames) {
    const schemaName = schemaNameForFixture(filename);
    const fixture = await loadJson(path.join("fixtures", "invalid", filename));
    assert.equal(validators[schemaName](fixture), false, `${filename} unexpectedly passed`);
  }
});

test("manifest and request schema expose the same v1 method allowlist", () => {
  const schemaMethods = schemas.request.properties.method.enum;
  assert.deepEqual([...schemaMethods].sort(), [...manifest.methods].sort());
  assert.equal(schemaMethods.includes("openAuth"), false);
  assert.equal(schemaMethods.includes("executeNativeMethod"), false);
});

test("phase 4 separates built-in code from advertised capabilities", () => {
  assert.deepEqual(manifest.phase0Capabilities, []);
  assert.deepEqual(manifest.builtInCapabilities, [
    "getNotificationStatus",
    "openPayment",
    "requestLocation",
    "requestNotificationPermission",
    "share",
  ]);
  assert.deepEqual(manifest.advertisedCapabilities, [
    "getNotificationStatus",
    "requestLocation",
    "requestNotificationPermission",
    "share",
  ]);
  // Kept as a compatibility alias for shells pinned before artifact 1.3.0.
  assert.deepEqual(manifest.compiledCapabilities, [
    "getNotificationStatus",
    "requestLocation",
    "requestNotificationPermission",
    "share",
  ]);
  for (const capability of manifest.builtInCapabilities) {
    assert.equal(manifest.methods.includes(capability), true, `${capability} is not a v1 method`);
  }
  assert.equal(manifest.advertisedCapabilities.includes("openPayment"), false);
  assert.equal(manifest.builtInCapabilities.includes("openAuth"), false);
});

test("manifest defines one deterministic handshake bootstrap transport", () => {
  assert.deepEqual(manifest.handshake, {
    transport: "dom-event",
    eventName: "foodhome:bridge-ready",
  });
});

test("manifest defines one acknowledged native-event transport", () => {
  assert.deepEqual(manifest.nativeEvents, {
    transport: "dom-event",
    eventName: "foodhome:native-event",
  });
  assert.ok(manifest.methods.includes("ackNativeEvent"));
  assert.ok(manifest.methods.includes("clearPaymentRecovery"));
});

test("artifact version and additive bridge major stay synchronized", () => {
  assert.equal(manifest.contractVersion, "1.4.0");
  assert.equal(manifest.contractVersion, packageMetadata.version);
  assert.equal(packageLock.version, packageMetadata.version);
  assert.equal(packageLock.packages[""].version, packageMetadata.version);
  assert.equal(manifest.bridgeMajor, 1);
  assert.deepEqual(manifest.supportedVersions, [1]);
});

test("native mode has one early non-authoritative bootstrap contract", () => {
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
  assert.equal(manifest.nativeMode.globalObjectName.includes("Bridge"), false);
  assert.equal(manifest.nativeMode.userAgentProduct.includes(" "), false);
});

test("sensitive v1 methods have bounded rate-limit policy", () => {
  for (const [method, limit] of Object.entries(manifest.rateLimits)) {
    assert.equal(manifest.methods.includes(method), true, `${method} is not a v1 method`);
    assert.equal(Number.isInteger(limit.maxRequests) && limit.maxRequests > 0, true);
    assert.equal(Number.isInteger(limit.windowSeconds) && limit.windowSeconds > 0, true);
  }
  assert.deepEqual(
    Object.keys(manifest.rateLimits).sort(),
    ["openPayment", "requestLocation", "requestNotificationPermission"],
  );
});

test("manifest and event schema expose the same event allowlist", () => {
  assert.deepEqual(
    [...schemas["native-event"].properties.name.enum].sort(),
    [...manifest.events].sort(),
  );
});

test("manifest and response schema expose the same typed errors", () => {
  assert.deepEqual(
    [...schemas.response.properties.error.properties.code.enum].sort(),
    [...manifest.errorCodes].sort(),
  );
});

test("message size is measured in UTF-8 bytes", () => {
  const maximum = manifest.limits.maxMessageBytes;
  assert.equal(isMessageWithinLimit("a".repeat(maximum), maximum), true);
  assert.equal(isMessageWithinLimit("я".repeat(maximum), maximum), false);
});

test("manifest bounds JSON depth and node count", () => {
  assert.equal(Number.isInteger(manifest.limits.maxJsonDepth), true);
  assert.equal(Number.isInteger(manifest.limits.maxJsonNodes), true);
  assert.equal(manifest.limits.maxJsonDepth >= 4 && manifest.limits.maxJsonDepth <= 32, true);
  assert.equal(manifest.limits.maxJsonNodes >= 64 && manifest.limits.maxJsonNodes <= 2048, true);
  assert.equal(
    isJsonStructureSafe({ protocol: "foodhome.bridge", payload: {} }, {
      maxDepth: manifest.limits.maxJsonDepth,
      maxNodes: manifest.limits.maxJsonNodes,
    }),
    true,
  );
});

test("missing or invalid remote config keeps base web and fails optional capabilities closed", () => {
  for (const config of [undefined, null, {}]) {
    assert.deepEqual(
      resolveRemoteConfig({
        config,
        schemaValid: false,
        now: "2026-08-28T12:00:00Z",
        compiledCapabilities: new Set(["share"]),
      }),
      SAFE_REMOTE_CONFIG,
    );
  }
});

test("expired remote config is ignored", async () => {
  const config = await loadJson("fixtures/invalid/remote-config-expired.json");
  assert.equal(validators["remote-config"](config), true, "expired config is structurally valid");
  assert.deepEqual(
    resolveRemoteConfig({
      config,
      schemaValid: true,
      now: "2026-08-28T12:00:00Z",
      compiledCapabilities: new Set(["share"]),
    }),
    SAFE_REMOTE_CONFIG,
  );
});

test("remote config can only enable the intersection with compiled capabilities", async () => {
  const config = await loadJson("fixtures/valid/remote-config.json");
  const resolved = resolveRemoteConfig({
    config,
    schemaValid: validators["remote-config"](config),
    now: "2026-08-28T12:00:00Z",
    builtInCapabilities: new Set(["share", "requestLocation", "openPayment"]),
    providerPolicyCapabilities: new Set(["share", "requestLocation"]),
  });

  assert.deepEqual(resolved.enabledCapabilities, ["share"]);
  assert.equal(resolved.baseWebEnabled, true);
  assert.equal(resolved.updateMode, "soft");
  assert.equal(resolved.paymentFlowVersion, "disabled");
});

test("remote config cannot make built-in payment effective without provider policy", async () => {
  const config = await loadJson("fixtures/valid/remote-config.json");
  const resolved = resolveRemoteConfig({
    config,
    schemaValid: validators["remote-config"](config),
    now: "2026-08-28T12:00:00Z",
    builtInCapabilities: new Set(["share", "openPayment"]),
    providerPolicyCapabilities: new Set(["share"]),
  });

  assert.deepEqual(resolved.enabledCapabilities, ["share"]);
  assert.equal(resolved.paymentFlowVersion, "disabled");
});

test("a request receives at most one terminal response", () => {
  const guard = new TerminalResponseGuard();
  assert.equal(guard.complete("request-1"), true);
  assert.equal(guard.complete("request-1"), false);
  assert.equal(guard.complete("request-2"), true);
});

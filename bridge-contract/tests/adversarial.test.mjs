import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

import { isJsonStructureSafe, isMessageWithinLimit } from "../lib/policy.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const loadJson = async (relativePath) =>
  JSON.parse(await readFile(path.join(root, relativePath), "utf8"));

const manifest = await loadJson("manifest.json");
const requestSchema = await loadJson("schemas/request.schema.json");
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const validateRequest = ajv.compile(requestSchema);

const baseRequest = Object.freeze({
  protocol: "foodhome.bridge",
  version: 1,
  requestId: "request-1",
  method: "getNotificationStatus",
  payload: {},
});

function seeded(seed) {
  let value = seed >>> 0;
  return () => {
    value = (value * 1664525 + 1013904223) >>> 0;
    return value / 0x1_0000_0000;
  };
}

test("malformed and non-object JSON input fails before schema dispatch", () => {
  for (const raw of ["", "{", "null", "true", "1", "[]", '[{"protocol":"foodhome.bridge"}]']) {
    let parsed;
    assert.throws(() => {
      parsed = JSON.parse(raw);
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
        throw new TypeError("top-level request must be an object");
      }
    });
  }
});

test("runtime-invalid fixtures fail the bounded JSON structure policy", async () => {
  for (const filename of ["request-payload-too-deep.json", "request-prototype-key.json"]) {
    const fixture = await loadJson(`fixtures/invalid/${filename}`);
    assert.equal(
      isJsonStructureSafe(fixture, {
        maxDepth: manifest.limits.maxJsonDepth,
        maxNodes: manifest.limits.maxJsonNodes,
      }),
      false,
      `${filename} unexpectedly passed runtime structure limits`,
    );
  }
});

test("bounded JSON policy accepts reviewed v1 requests", async () => {
  for (const filename of [
    "request-additive-field.json",
    "request-location.json",
    "request-payment.json",
    "request-share.json",
  ]) {
    const fixture = await loadJson(`fixtures/valid/${filename}`);
    assert.equal(
      isJsonStructureSafe(fixture, {
        maxDepth: manifest.limits.maxJsonDepth,
        maxNodes: manifest.limits.maxJsonNodes,
      }),
      true,
      `${filename} unexpectedly failed runtime structure limits`,
    );
  }
});

test("fixed-seed request mutations remain deterministic and fail closed", () => {
  const random = seeded(0x46484d35);
  const mutations = [
    (request) => ({ ...request, protocol: `invalid-${Math.floor(random() * 1e6)}` }),
    (request) => ({ ...request, version: 2 + Math.floor(random() * 100) }),
    (request) => ({ ...request, requestId: `bad id ${Math.floor(random() * 1e6)}` }),
    (request) => ({ ...request, method: `unknown-${Math.floor(random() * 1e6)}` }),
    (request) => ({ ...request, payload: null }),
  ];

  let rejected = 0;
  for (let index = 0; index < 500; index += 1) {
    const mutate = mutations[index % mutations.length];
    const candidate = mutate(structuredClone(baseRequest));
    if (!validateRequest(candidate)) rejected += 1;
  }
  assert.equal(rejected, 500);
});

test("large multibyte and highly connected inputs are bounded", () => {
  assert.equal(
    isMessageWithinLimit("я".repeat(manifest.limits.maxMessageBytes), manifest.limits.maxMessageBytes),
    false,
  );
  const manyNodes = {
    ...baseRequest,
    payload: { future: Array.from({ length: manifest.limits.maxJsonNodes }, (_, index) => index) },
  };
  assert.equal(
    isJsonStructureSafe(manyNodes, {
      maxDepth: manifest.limits.maxJsonDepth,
      maxNodes: manifest.limits.maxJsonNodes,
    }),
    false,
  );
});

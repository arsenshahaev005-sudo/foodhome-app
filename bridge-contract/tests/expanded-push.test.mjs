import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import test from "node:test";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = new URL("../", import.meta.url);
const json = async (path) => JSON.parse(await readFile(new URL(path, root), "utf8"));
const ajv = new Ajv2020({ strict: true, allErrors: true });
addFormats(ajv);
const broadcast = ajv.compile(await json("schemas/push-v6.schema.json"));
const buyer = ajv.compile(await json("schemas/push-v7.schema.json"));
const v6 = await json("fixtures/valid/push-broadcast.json");
const v7 = await json("fixtures/valid/push-buyer.json");

test("canonical imported schemas and documents preserve Git bytes and source provenance", async () => {
  const provenance = await json("provenance/push-v6-v7.json");
  assert.equal(provenance.source.commit, "e245b0e859de93ecd193538fb18aef7b5c9bbdbd");
  assert.equal(provenance.source.kind, "git-commit");
  assert.equal(provenance.files.length, 8);
  // Package tests must also work in npm pack without the repository's docs/.
  // The repository-level verifier separately checks all eight original blobs.
  for (const file of provenance.files.filter((entry) => entry.targetPath.startsWith("bridge-contract/"))) {
    const bytes = await readFile(new URL(file.targetPath.slice("bridge-contract/".length), root));
    assert.equal(createHash("sha256").update(bytes).digest("hex"), file.sha256, file.targetPath);
    assert.equal(bytes.includes(Buffer.from("\r\n")), false, file.targetPath);
  }
});

test("v6 accepts exactly the three role hints and no caller URL", () => {
  for (const role of ["ACCOUNT", "CLIENT", "SELLER"]) {
    assert.equal(broadcast({ ...v6, route: { ...v6.route, query: { role } } }), true);
  }
  for (const role of ["ADMIN", "BUYER", "seller", "", 1, true, null, {}, []]) {
    assert.equal(broadcast({ ...v6, route: { ...v6.route, query: { role } } }), false);
  }
});

test("v7 permits only client inbox or an order UUID, without role-based authorization claims", () => {
  assert.equal(buyer(v7), true);
  const order = { ...v7, route: { ...v7.route, name: "order.detail", params: { id: v7.eventId }, query: {} } };
  assert.equal(buyer(order), true);
  assert.equal(broadcast(order), false);
  for (const role of ["ACCOUNT", "SELLER"]) assert.equal(buyer({ ...v7, route: { ...v7.route, query: { role } } }), false);
});

test("six fields only, fixed version-event pairs, strict JSON types and private data rejection", () => {
  for (const [value, validate] of [[v6, broadcast], [v7, buyer]]) {
    assert.equal(validate(value), true);
    for (const field of Object.keys(value)) {
      const missing = { ...value }; delete missing[field];
      assert.equal(validate(missing), false, `missing ${field}`);
      for (const wrong of [null, true, [], {}]) assert.equal(validate({ ...value, [field]: wrong }), false, field);
    }
    for (const field of ["title", "body", "notification", "url", "sound", "promoCode", "amount", "address", "message"]) {
      assert.equal(validate({ ...value, [field]: "private" }), false, field);
    }
    for (const wrong of [1, 2, 3, 4, 5, 8, String(value.version)]) assert.equal(validate({ ...value, version: wrong }), false);
    for (const wrong of ["chat.message", "seller.order.new", "payment.success", value === v6 ? "buyer.notification" : "broadcast.notification"]) {
      assert.equal(validate({ ...value, eventType: wrong }), false);
    }
    for (const field of ["eventId", "bindingId"]) for (const wrong of ["../auth", "", "A".repeat(64), 4]) {
      assert.equal(validate({ ...value, [field]: wrong }), false);
    }
    for (const wrong of ["not-a-date", 123]) assert.equal(validate({ ...value, expiresAt: wrong }), false);
    for (const route of ["https://evil.test", { ...value.route, url: "https://evil.test" },
      { ...value.route, protocol: "https" }, { ...value.route, version: "1" },
      { ...value.route, params: { id: "../auth" } }, { ...value.route, query: { role: "CLIENT", url: "https://evil.test" } }]) {
      assert.equal(validate({ ...value, route }), false);
    }
    // Match Python json.dumps(ensure_ascii=True,separators=(",",":"),sort_keys=True).
    const ordered = (item) => item && typeof item === "object" && !Array.isArray(item)
      ? Object.fromEntries(Object.keys(item).sort().map((key) => [key, ordered(item[key])])) : item;
    const wire = Object.fromEntries(Object.entries(value).map(([key, item]) => [key, key === "route" ? JSON.stringify(ordered(item)) : String(item)]));
    assert.equal(wire.route, `{"name":"notifications.inbox","params":{},"protocol":"foodhome.logical-route","query":{"role":"${value === v6 ? "SELLER" : "CLIENT"}"},"version":1}`);
    assert.equal(Object.keys(wire).length, 6);
    assert.ok(Object.values(wire).every((item) => typeof item === "string"));
    assert.deepEqual(JSON.parse(wire.route), value.route);
    assert.ok(Object.entries(wire).reduce((n, [k, v]) => n + Buffer.byteLength(k) + Buffer.byteLength(v), 0) <= 2048);
  }
});

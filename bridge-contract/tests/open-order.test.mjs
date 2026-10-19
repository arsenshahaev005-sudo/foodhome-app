import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
const root = new URL("../", import.meta.url);
const load = async (file) => JSON.parse(await readFile(new URL(file, root), "utf8"));
const ajv = new Ajv2020({ strict: true, allErrors: true });
addFormats(ajv);
const validate = ajv.compile(await load("schemas/push-v5.schema.json"));
test("open-order push keeps six fields, correct role, expiry, and no private content", async () => {
  for (const name of ["buyer", "seller", "moderated"]) {
    const input = await load(`fixtures/valid/push-openorder-${name}.json`);
    assert.equal(validate(input), true, ajv.errorsText(validate.errors));
    const other = input.route.name === "open-order.buyer" ? "open-order.seller" : "open-order.buyer";
    for (const invalid of [
      ...[1, 2, 3, 4].map((version) => ({ ...input, version })),
      { ...input, eventType: "seller.order.new" },
      { ...input, body: "private" }, { ...input, expiresAt: "never" },
      { ...input, route: { ...input.route, name: other } },
      { ...input, route: { ...input.route, params: { id: "../auth" } } },
      { ...input, route: { ...input.route, query: { role: "seller" } } },
    ]) assert.equal(validate(invalid), false);
  }
});

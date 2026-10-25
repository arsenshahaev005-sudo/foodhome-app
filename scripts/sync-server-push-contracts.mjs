import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const commit = "e245b0e859de93ecd193538fb18aef7b5c9bbdbd";
const provenancePath = resolve(root, "bridge-contract/provenance/push-v6-v7.json");
const files = [
  ["backend/api/contracts/native_push/v6/payload.schema.json", "bridge-contract/schemas/push-v6.schema.json"],
  ["backend/api/contracts/native_push/v6/fixtures/valid/push-broadcast.json", "bridge-contract/fixtures/valid/push-broadcast.json"],
  ["backend/api/contracts/native_push/v7/payload.schema.json", "bridge-contract/schemas/push-v7.schema.json"],
  ["backend/api/contracts/native_push/v7/fixtures/valid/push-buyer.json", "bridge-contract/fixtures/valid/push-buyer.json"],
  ["docs/integration/admin-broadcast-push-v6.md", "docs/integration/server-admin-broadcast-push-v6.md"],
  ["docs/integration/admin-broadcast-push-v6.sha256", "docs/integration/server-admin-broadcast-push-v6.sha256"],
  ["docs/integration/buyer-notifications-v7.md", "docs/integration/server-buyer-notifications-v7.md"],
  ["docs/integration/buyer-notifications-v7.sha256", "docs/integration/server-buyer-notifications-v7.sha256"],
];
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
// Import is an explicit byte-preserving mechanical copy, never a sibling checkout/edit.
// Normal CI mode only verifies the vendored files and requires no remote repository.
if (process.argv[2] === "--import-from") {
  assert.equal(process.argv.length, 4);
  const source = resolve(process.argv[3]);
  const git = (...args) => execFileSync("git", ["-c", `safe.directory=${source.replaceAll("\\", "/")}`, "-C", source, ...args]);
  const blobs = files.map(([sourcePath, targetPath]) => ({
    sourcePath, targetPath, bytes: git("show", `${commit}:${sourcePath}`),
    gitBlob: git("rev-parse", `${commit}:${sourcePath}`).toString().trim(),
  }));
  for (const manifest of blobs.filter((file) => file.sourcePath.endsWith(".sha256"))) {
    for (const line of manifest.bytes.toString("utf8").trim().split(/\r?\n/)) {
      const [, digest, path] = /^(\w{64})  (.+)$/.exec(line) ?? [];
      const blob = blobs.find((file) => file.sourcePath === path);
      assert.ok(blob, `Unimported manifest input: ${path}`);
      assert.equal(sha256(blob.bytes), digest, `Canonical Git bytes mismatch: ${path}`);
    }
  }
  const metadata = {
    source: { repository: "https://github.com/arsenshahaev005-sudo/food-home", commit, kind: "git-commit" },
    encoding: "Exact Git blob bytes, not Windows working-tree/CRLF bytes",
    files: blobs.map(({ bytes, ...file }) => ({ ...file, sha256: sha256(bytes) })),
  };
  for (const file of blobs) {
    const target = resolve(root, file.targetPath);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, file.bytes);
  }
  mkdirSync(dirname(provenancePath), { recursive: true });
  writeFileSync(provenancePath, `${JSON.stringify(metadata, null, 2)}\n`);
} else {
  assert.equal(process.argv.length, 2, "Use no arguments to verify, or --import-from <read-only source repo>");
}
const provenance = JSON.parse(readFileSync(provenancePath, "utf8"));
assert.equal(provenance.source.commit, commit);
assert.equal(provenance.source.kind, "git-commit");
assert.deepEqual(provenance.files.map(({ sourcePath, targetPath }) => [sourcePath, targetPath]), files);
for (const file of provenance.files) {
  assert.match(file.gitBlob, /^[0-9a-f]{40}$/);
  assert.match(file.sha256, /^[0-9a-f]{64}$/);
  assert.equal(sha256(readFileSync(resolve(root, file.targetPath))), file.sha256, file.targetPath);
}
console.log(`Verified ${files.length} canonical Git blobs from food-home@${commit}`);

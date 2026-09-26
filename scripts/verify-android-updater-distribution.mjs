import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const mode = process.argv[2];
assert.ok(['direct', 'store'].includes(mode), 'Specify direct or store');
const manifest = readFileSync(resolve(root,
  'android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml'), 'utf8');
const config = readFileSync(resolve(root,
  'android/app/build/generated/source/buildConfig/release/market/foodhome/app/BuildConfig.java'), 'utf8');
const enabled = mode === 'direct';
assert.equal(manifest.includes('android.permission.REQUEST_INSTALL_PACKAGES'), enabled,
  'Store builds must not request APK install permission; direct builds must include it');
assert.ok(config.includes(`DIRECT_APK_UPDATES_ENABLED = ${enabled};`), 'Runtime updater must match distribution');
assert.ok(config.includes('DEBUG = false;'), 'Verify release rather than debug');
console.log(`Android ${mode} update distribution verified.`);

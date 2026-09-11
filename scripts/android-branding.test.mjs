import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = file => readFileSync(path.join(root, file), 'utf8');
const res = 'android/app/src/main/res/';
test('vendored PWA assets retain source hashes and icon dimensions', () => {
  const provenance = JSON.parse(read('assets/branding/provenance.json'));
  for (const asset of provenance.assets) {
    let data = readFileSync(path.join(root, asset.local));
    if (asset.normalization) data = Buffer.from(data.toString('utf8').replace(/\r\n/g, '\n'));
    assert.equal(createHash('sha256').update(data).digest('hex'), asset.sha256, asset.local);
    if (asset.local.endsWith('.png')) {
      assert.equal(data.subarray(1, 4).toString(), 'PNG');
      assert.equal(data.readUInt32BE(16), 512);
      assert.equal(data.readUInt32BE(20), 512);
    }
  }
});
test('vector resources are deterministic conversions, including splash safe zone', () => {
  execFileSync(process.execPath, [path.join(root, 'scripts/generate-android-branding.mjs'), '--check']);
  const splash = read(res + 'drawable/foodhome_splash_wordmark.xml');
  assert(!/^\+/m.test(splash), 'No stray text nodes before the XML root');
  assert.match(splash, /android:scaleX="0.64"/);
  // The whole wordmark fits inside the 192dp-diameter safe circle of a 288dp icon.
  assert(Math.hypot(288 * 0.64, 288 * 0.64 * 500 / 2850) < 192);
});
test('one launcher activity uses the compatible splash and a shared adaptive icon', () => {
  const manifest = read('android/app/src/main/AndroidManifest.xml');
  assert.equal((manifest.match(/<activity\s/g) ?? []).length, 1);
  assert.match(manifest, /android:icon="@mipmap\/ic_launcher"/);
  assert.match(manifest, /android:roundIcon="@mipmap\/ic_launcher"/);
  assert.match(manifest, /android:theme="@style\/Theme.FoodHome.Starting"/);
  assert.match(read(res + 'mipmap-anydpi-v26/ic_launcher.xml'), /android:inset="12.5%"/);
  const activity = read('android/app/src/main/java/market/foodhome/app/MainActivity.kt');
  assert(activity.indexOf('installSplashScreen()') < activity.indexOf('super.onCreate(savedInstanceState)'));
  assert(!activity.includes('setKeepOnScreenCondition'));
});
test('launch uses PWA colors and local assets without timers or a second WebView', () => {
  const surface = read('android/app/src/main/java/market/foodhome/app/ui/FoodHomeLaunchSurface.kt');
  assert.match(read(res + 'values/colors.xml'), /#FFF7ED/);
  assert.match(surface, /R.drawable.foodhome_wordmark/);
  assert.match(surface, /repeat\(3\)/);
  assert.match(surface, /ProgressBarRangeInfo.Indeterminate/);
  assert(!/https?:|WebView|delay\(|Thread.sleep|Handler\(/.test(surface));
  const states = read('android/app/src/main/java/market/foodhome/app/ui/AppShellSurface.kt');
  assert.match(states, /AppShellState.Content -> Unit/);
  assert.match(states, /AppShellState.Loading -> FoodHomeLaunchSurface\(\)/);
  assert.match(states, /AppShellState.Offline -> RecoveryPanel/);
});

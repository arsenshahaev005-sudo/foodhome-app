import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const resource = 'android/app/src/main/res/raw/seller_new_order.wav';

test('seller notification sound is the unchanged owner-provided PCM WAV', async () => {
  const bytes = await readFile(new URL(resource, root));
  assert.equal(createHash('sha256').update(bytes).digest('hex'),
    '7fb0a3e173d9cb882b3a95aa55072f34b536bcb9842c3729c10c6a3829559710');
  assert.equal(bytes.toString('ascii', 0, 4), 'RIFF');
  assert.equal(bytes.toString('ascii', 8, 12), 'WAVE');
  assert.equal(bytes.readUInt32LE(4) + 8, bytes.length);
  let format, data;
  for (let offset = 12; offset + 8 <= bytes.length;) {
    const id = bytes.toString('ascii', offset, offset + 4);
    const size = bytes.readUInt32LE(offset + 4);
    assert.ok(offset + 8 + size <= bytes.length);
    if (id === 'fmt ') {
      format = bytes.subarray(offset + 8, offset + 8 + size);
    } else if (id === 'data') data = size;
    offset += 8 + size + size % 2;
  }
  assert.ok(format && data);
  assert.equal(format.readUInt16LE(0), 1); // PCM
  assert.equal(format.readUInt16LE(2), 1); // mono
  assert.equal(format.readUInt32LE(4), 44100);
  assert.equal(format.readUInt16LE(14), 16);
  const duration = data / format.readUInt32LE(8);
  assert.ok(duration > 4 && duration < 4.04);
});

test('named sound survives Android resource shrinking', async () => {
  const keep = await readFile(new URL('android/app/src/main/res/raw/keep.xml', root), 'utf8');
  assert.match(keep, /tools:keep="@raw\/seller_new_order"/);
});

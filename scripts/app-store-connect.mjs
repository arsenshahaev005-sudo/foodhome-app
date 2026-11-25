import { sign } from 'node:crypto';
import { readFileSync } from 'node:fs';

const targets = JSON.parse(readFileSync(new URL('./ios_distribution.json', import.meta.url), 'utf8'));

export function distributionTarget(name, audience) {
  if (!Object.hasOwn(targets, name)) throw new Error('Неизвестное окружение iOS.');
  const target = targets[name];
  if (!target.audiences.includes(audience)) {
    throw new Error('Недопустимая аудитория. Food&Home Staging доступен только для внутреннего TestFlight.');
  }
  return target;
}

function token() {
  const now = Math.floor(Date.now() / 1000);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const { ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY } = process.env;
  if (!ASC_KEY_ID || !ASC_ISSUER_ID || !ASC_PRIVATE_KEY) throw new Error('Отсутствуют данные доступа к App Store Connect.');
  const header = encode({ alg: 'ES256', kid: ASC_KEY_ID, typ: 'JWT' });
  const claims = encode({ iss: ASC_ISSUER_ID, iat: now, exp: now + 300, aud: 'appstoreconnect-v1' });
  const message = header + '.' + claims;
  const signature = sign('sha256', Buffer.from(message), { key: ASC_PRIVATE_KEY, dsaEncoding: 'ieee-p1363' });
  return message + '.' + signature.toString('base64url');
}

export async function get(resource, params) {
  const url = new URL('https://api.appstoreconnect.apple.com/v1/' + resource);
  url.search = new URLSearchParams(params);
  const response = await fetch(url, {
    headers: { Authorization: 'Bearer ' + token() }, redirect: 'error', signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    const error = new Error('App Store Connect отклонил запрос ' + resource + ' (HTTP ' + response.status + ').');
    error.retryable = response.status === 429 || response.status >= 500;
    throw error;
  }
  return response.json();
}

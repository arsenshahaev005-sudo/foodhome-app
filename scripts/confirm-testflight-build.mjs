import { sign } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';

const { ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY, APP_BUNDLE_ID, APP_VERSION, BUILD_NUMBER } = process.env;
const audiences = { internal: 'INTERNAL_ONLY', 'app-store': 'APP_STORE_ELIGIBLE' };
const expectedAudience = audiences[process.env.DISTRIBUTION_AUDIENCE ?? 'internal'];
if (!expectedAudience) throw new Error('Unknown iOS distribution audience.');
const base64url = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');

function token() {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url({ alg: 'ES256', kid: ASC_KEY_ID, typ: 'JWT' });
  const claims = base64url({ iss: ASC_ISSUER_ID, iat: now, exp: now + 300, aud: 'appstoreconnect-v1' });
  const message = header + '.' + claims;
  const signature = sign('sha256', Buffer.from(message), {
    key: ASC_PRIVATE_KEY, dsaEncoding: 'ieee-p1363',
  });
  return message + '.' + signature.toString('base64url');
}

async function get(resource, params) {
  const url = new URL('https://api.appstoreconnect.apple.com/v1/' + resource);
  url.search = new URLSearchParams(params);
  const response = await fetch(url, {
    headers: { Authorization: 'Bearer ' + token() }, redirect: 'error', signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    const error = new Error('App Store Connect ' + resource + ' returned HTTP ' + response.status);
    error.retryable = response.status === 429 || response.status >= 500;
    throw error;
  }
  return response.json();
}

const apps = await get('apps', { 'filter[bundleId]': APP_BUNDLE_ID, 'fields[apps]': 'bundleId' });
if (apps.data.length !== 1) throw new Error('Expected one app for the configured bundle identifier.');
const appId = apps.data[0].id;

async function findBuild() {
  const { data, included } = await get('builds', {
    'filter[app]': appId, 'filter[version]': BUILD_NUMBER, include: 'preReleaseVersion,buildBetaDetail',
    'fields[builds]': 'version,processingState,buildAudienceType,preReleaseVersion,buildBetaDetail',
    'fields[preReleaseVersions]': 'version,platform', 'fields[buildBetaDetails]': 'internalBuildState',
  });
  for (const build of data) {
    const version = included?.find((row) => row.type === 'preReleaseVersions' &&
      row.id === build.relationships.preReleaseVersion.data?.id);
    if (version?.attributes.version !== APP_VERSION || version.attributes.platform !== 'IOS') continue;
    const details = included?.find((row) => row.type === 'buildBetaDetails' &&
      row.id === build.relationships.buildBetaDetail?.data?.id);
    return { ...build, internalState: details?.attributes.internalBuildState };
  }
}

const deadline = Date.now() + 40 * 60_000;
let previous;
while (Date.now() < deadline) {
  let build;
  try {
    build = await findBuild();
  } catch (error) {
    if (!error.retryable && !(error instanceof TypeError) && error.name !== 'TimeoutError') throw error;
    console.log('Apple status temporarily unavailable; retrying.');
    await delay(30_000);
    continue;
  }
  const state = build?.attributes.processingState ?? 'awaiting build record';
  if (state !== previous) console.log('Apple processing state:', state);
  previous = state;
  if (state === 'VALID') {
    if (build.attributes.buildAudienceType !== expectedAudience) throw new Error('Unexpected build audience.');
    const link = 'https://appstoreconnect.apple.com/apps/' + appId + '/testflight/ios/' + build.id;
    const message = 'Food Home ' + APP_VERSION + ' (' + BUILD_NUMBER + ') — ' + state + ', ' + expectedAudience + '. ' +
      'Internal testing status: ' + (build.internalState ?? 'pending') + '. ' + link;
    console.log(message);
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, message + '\n');
    process.exit(0);
  }
  if (state === 'FAILED' || state === 'INVALID') throw new Error('Apple processing ended with ' + state);
  await delay(30_000);
}
throw new Error('Apple processing is still pending. Re-run only the confirmation job to check this upload.');

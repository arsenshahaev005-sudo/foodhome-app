import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { confirm, distributionRequest, findAppId, findBuild } from './confirm-testflight-build.mjs';
import { downloadStagingProfiles } from './fetch-staging-profiles.mjs';

const app = 'market.foodhome.app.staging';
const names = ['FoodHome Staging AppStore', 'FoodHome Staging OrderStatus AppStore'];
const env = { DISTRIBUTION_TARGET: 'staging', DISTRIBUTION_AUDIENCE: 'internal', APP_VERSION: '1.0', BUILD_NUMBER: '7.1' };

async function temp(t) {
  const dir = await mkdtemp(join(tmpdir(), 'foodhome-distribution-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  return dir;
}

test('production stays the default and staging accepts only internal builds and valid confirmation parameters', () => {
  assert.equal(distributionRequest({}).target.bundleId, 'market.foodhome.app');
  assert.equal(distributionRequest({ DISTRIBUTION_AUDIENCE: 'app-store' }).expectedAudience, 'APP_STORE_ELIGIBLE');
  assert.equal(distributionRequest({ ...env, DISTRIBUTION_OPERATION: 'confirm' }).target.displayName, 'Food&Home Staging');
  for (const change of [{ DISTRIBUTION_TARGET: 'other' }, { DISTRIBUTION_AUDIENCE: 'app-store' },
    { DISTRIBUTION_OPERATION: 'submit' }, { DISTRIBUTION_OPERATION: 'confirm', BUILD_NUMBER: '' },
    { DISTRIBUTION_OPERATION: 'confirm', APP_VERSION: '1.0\nfoo' }]) {
    assert.throws(() => distributionRequest({ ...env, ...change }));
  }
});

function profileResponse(params) {
  const index = names.indexOf(params['filter[name]']);
  assert.notEqual(index, -1);
  assert.equal(params['filter[profileState]'], 'ACTIVE');
  assert.equal(params['filter[profileType]'], 'IOS_APP_STORE');
  const id = 'bundle-' + index;
  return { data: [{ type: 'profiles', attributes: { name: names[index], profileState: 'ACTIVE', profileType: 'IOS_APP_STORE',
    profileContent: Buffer.from('fake-profile-' + index).toString('base64') },
    relationships: { bundleId: { data: { type: 'bundleIds', id } } } }],
    included: [{ type: 'bundleIds', id, attributes: { identifier: index === 0 ? app : app + '.OrderStatus' } }] };
}

test('only the two named staging distribution profiles are requested and written', async t => {
  const directory = await temp(t);
  const seen = [];
  await downloadStagingProfiles(directory, async (resource, params) => {
    assert.equal(resource, 'profiles');
    seen.push(params['filter[name]']);
    return profileResponse(params);
  });
  assert.deepEqual(seen.sort(), [...names].sort());
  assert.equal(await readFile(join(directory, 'profile.mobileprovision'), 'utf8'), 'fake-profile-0');
  assert.equal(await readFile(join(directory, 'widget.mobileprovision'), 'utf8'), 'fake-profile-1');
});

test('an ambiguous, wrong or corrupt profile writes neither file', async t => {
  const directory = await temp(t);
  for (const change of [
    response => { response.included[0].attributes.identifier = 'market.foodhome.app'; },
    response => { response.data.push(response.data[0]); },
    response => { response.data[0].attributes.name = 'Production AppStore'; },
    response => { response.data[0].attributes.profileState = 'INVALID'; },
    response => { response.data[0].attributes.profileContent = 'not base64!'; },
  ]) {
    await assert.rejects(downloadStagingProfiles(directory, async (_resource, params) => {
      const result = profileResponse(params);
      if (params['filter[name]'] === names[1]) change(result);
      return result;
    }));
    assert.deepEqual(await readdir(directory), []);
  }
});

function appResponse(bundleId = app) {
  return { data: [{ type: 'apps', id: 'stage-app', attributes: { bundleId } }] };
}

function buildResponse() {
  return { data: [{ type: 'builds', id: 'stage-build', attributes: {
    version: '7.1', processingState: 'VALID', buildAudienceType: 'INTERNAL_ONLY',
  }, relationships: {
    app: { data: { type: 'apps', id: 'stage-app' } }, preReleaseVersion: { data: { id: 'release' } },
    buildBetaDetail: { data: { id: 'beta' } },
  } }], included: [
    { type: 'preReleaseVersions', id: 'release', attributes: { version: '1.0', platform: 'IOS' } },
    { type: 'buildBetaDetails', id: 'beta', attributes: { internalBuildState: 'IN_BETA_TESTING' } },
  ] };
}

test('a same-number production app, other platform or different marketing version cannot confirm staging', async () => {
  await assert.rejects(findAppId(app, async () => appResponse('market.foodhome.app')));
  for (const change of [
    response => { response.data[0].relationships.app.data.id = 'production-app'; },
    response => { response.data[0].attributes.version = '8.1'; },
    response => { response.included[0].attributes.platform = 'MAC_OS'; },
    response => { response.included[0].attributes.version = '2.0'; },
  ]) {
    const response = buildResponse();
    change(response);
    assert.equal(await findBuild('stage-app', '1.0', '7.1', async () => response), undefined);
  }
});

test('confirmation writes the exact internal build and rejects a valid App Store eligible staging build', async t => {
  const directory = await temp(t);
  const summary = join(directory, 'summary');
  let audience = 'APP_STORE_ELIGIBLE';
  const read = async (resource, params) => {
    if (resource === 'apps') {
      assert.equal(params['filter[bundleId]'], app);
      return appResponse();
    }
    assert.equal(resource, 'builds');
    assert.equal(params['filter[app]'], 'stage-app');
    assert.equal(params['filter[version]'], '7.1');
    const response = buildResponse();
    response.data[0].attributes.buildAudienceType = audience;
    return response;
  };
  await assert.rejects(confirm({ ...env, GITHUB_STEP_SUMMARY: summary }, read), /аудиторию/);
  audience = 'INTERNAL_ONLY';
  await confirm({ ...env, GITHUB_STEP_SUMMARY: summary }, read);
  assert.match(await readFile(summary, 'utf8'), /Food&Home Staging 1\.0 \(7\.1\).*INTERNAL_ONLY.*stage-build/);
});

test('the default application still confirms an App Store eligible production release', async t => {
  const summary = join(await temp(t), 'summary');
  const settings = { ...env, DISTRIBUTION_TARGET: 'production', DISTRIBUTION_AUDIENCE: 'app-store', GITHUB_STEP_SUMMARY: summary };
  await confirm(settings, async (resource, params) => {
    if (resource === 'apps') {
      assert.equal(params['filter[bundleId]'], 'market.foodhome.app');
      return appResponse('market.foodhome.app');
    }
    const response = buildResponse();
    response.data[0].attributes.buildAudienceType = 'APP_STORE_ELIGIBLE';
    return response;
  });
  assert.match(await readFile(summary, 'utf8'), /Food&Home 1\.0 \(7\.1\).*APP_STORE_ELIGIBLE/);
});

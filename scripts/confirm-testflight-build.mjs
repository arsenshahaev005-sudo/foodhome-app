import { appendFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';
import { distributionTarget, get } from './app-store-connect.mjs';

const audiences = { internal: 'INTERNAL_ONLY', 'app-store': 'APP_STORE_ELIGIBLE' };
const versionPattern = /^\d+(?:\.\d+){0,2}$/;

export function distributionRequest(env) {
  const { DISTRIBUTION_TARGET = 'production', DISTRIBUTION_AUDIENCE = 'internal',
    DISTRIBUTION_OPERATION = 'upload', APP_VERSION, BUILD_NUMBER } = env;
  const target = distributionTarget(DISTRIBUTION_TARGET, DISTRIBUTION_AUDIENCE);
  if (!['upload', 'confirm'].includes(DISTRIBUTION_OPERATION)) throw new Error('Неизвестная операция TestFlight.');
  if (DISTRIBUTION_OPERATION === 'confirm' && (!versionPattern.test(APP_VERSION) || !versionPattern.test(BUILD_NUMBER))) {
    throw new Error('Для проверки укажите версию и номер сборки в числовом формате.');
  }
  return { target, expectedAudience: audiences[DISTRIBUTION_AUDIENCE] };
}

export async function findAppId(bundleId, read = get) {
  const { data } = await read('apps', { 'filter[bundleId]': bundleId, 'fields[apps]': 'bundleId', limit: '2' });
  if (data.length !== 1 || data[0].type !== 'apps' || data[0].attributes?.bundleId !== bundleId || !data[0].id) {
    throw new Error('В App Store Connect не найдена однозначная карточка ' + bundleId + '.');
  }
  return data[0].id;
}

export async function findBuild(appId, version, number, read = get) {
  const { data, included = [] } = await read('builds', {
    'filter[app]': appId, 'filter[version]': number, include: 'app,preReleaseVersion,buildBetaDetail',
    'fields[apps]': 'bundleId',
    'fields[builds]': 'version,processingState,buildAudienceType,app,preReleaseVersion,buildBetaDetail',
    'fields[preReleaseVersions]': 'version,platform', 'fields[buildBetaDetails]': 'internalBuildState',
  });
  for (const build of data) {
    const links = build.relationships;
    if (build.type !== 'builds' || !build.id || build.attributes?.version !== number ||
        links?.app?.data?.id !== appId || links.app.data.type !== 'apps') continue;
    const release = included.find(row => row.type === 'preReleaseVersions' && row.id === links.preReleaseVersion?.data?.id);
    if (release?.attributes.version !== version || release.attributes.platform !== 'IOS') continue;
    const detail = included.find(row => row.type === 'buildBetaDetails' && row.id === links.buildBetaDetail?.data?.id);
    return { ...build, internalState: detail?.attributes.internalBuildState };
  }
}

export async function confirm(env, read = get) {
  const { target, expectedAudience } = distributionRequest({ ...env, DISTRIBUTION_OPERATION: 'confirm' });
  const deadline = Date.now() + 40 * 60_000;
  let appId, previous;
  while (Date.now() < deadline) {
    let build;
    try {
      appId ??= await findAppId(target.bundleId, read);
      build = await findBuild(appId, env.APP_VERSION, env.BUILD_NUMBER, read);
    } catch (error) {
      if (!error.retryable && !(error instanceof TypeError) && error.name !== 'TimeoutError') throw error;
      console.log('Проверка Apple временно недоступна. Повтор через 30 секунд.');
      await delay(30_000);
      continue;
    }
    const state = build?.attributes.processingState ?? 'ожидается запись сборки';
    if (state !== previous) console.log('Статус обработки Apple:', state);
    previous = state;
    if (state === 'VALID') {
      if (build.attributes.buildAudienceType !== expectedAudience) throw new Error('Apple указала неожиданную аудиторию сборки.');
      const link = 'https://appstoreconnect.apple.com/apps/' + appId + '/testflight/ios/' + build.id;
      const message = target.displayName + ' ' + env.APP_VERSION + ' (' + env.BUILD_NUMBER + ') — ' + state + ', ' + expectedAudience +
        '. Внутреннее тестирование: ' + (build.internalState ?? 'ожидается') + '. ' + link;
      console.log(message);
      appendFileSync(env.GITHUB_STEP_SUMMARY, message + '\n');
      return;
    }
    if (state === 'FAILED' || state === 'INVALID') throw new Error('Apple завершила обработку со статусом ' + state + '.');
    await delay(30_000);
  }
  throw new Error('Apple ещё обрабатывает сборку. Повторите confirm с прежними версией и номером сборки.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv[2] === '--validate') distributionRequest(process.env);
  else await confirm(process.env);
}

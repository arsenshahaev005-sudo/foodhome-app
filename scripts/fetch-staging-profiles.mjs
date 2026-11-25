import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { distributionTarget, get } from './app-store-connect.mjs';

export async function downloadStagingProfiles(directory, read = get) {
  const target = distributionTarget('staging', 'internal');
  const bundles = [target.bundleId, target.bundleId + '.OrderStatus'];
  const files = ['profile.mobileprovision', 'widget.mobileprovision'];
  const profiles = await Promise.all(target.profileNames.map(async (name, index) => {
    const response = await read('profiles', {
      'filter[name]': name, 'filter[profileType]': 'IOS_APP_STORE', 'filter[profileState]': 'ACTIVE',
      'fields[profiles]': 'name,profileContent,profileType,profileState,bundleId',
      include: 'bundleId', 'fields[bundleIds]': 'identifier', limit: '2',
    });
    const rows = Array.isArray(response?.data) ? response.data : [];
    const [row] = rows;
    const attr = row?.attributes;
    const related = row?.relationships?.bundleId?.data;
    const bundle = response?.included?.find(item => item.type === 'bundleIds' && item.id === related?.id);
    if (rows.length !== 1 || response.links?.next || row.type !== 'profiles' ||
        attr?.name !== name || attr.profileType !== 'IOS_APP_STORE' || attr.profileState !== 'ACTIVE' ||
        related?.type !== 'bundleIds' || bundle?.attributes?.identifier !== bundles[index]) {
      throw new Error('В Apple нужен один активный профиль «' + name + '» для ' + bundles[index] + '.');
    }
    const content = attr.profileContent;
    if (typeof content !== 'string' || !content.length) throw new Error('Apple вернула пустой профиль «' + name + '».');
    const encoded = content.replace(/\s/g, '');
    const decoded = Buffer.from(encoded, 'base64');
    if (!decoded.length || decoded.toString('base64') !== encoded) throw new Error('Apple вернула повреждённый профиль «' + name + '».');
    return decoded;
  }));
  await mkdir(directory, { recursive: true, mode: 0o700 });
  await Promise.all(profiles.map((data, index) => writeFile(join(directory, files[index]), data, { mode: 0o600, flag: 'wx' })));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.env.DISTRIBUTION_TARGET !== 'staging') throw new Error('Загрузка профилей разрешена только для staging.');
  distributionTarget('staging', process.env.DISTRIBUTION_AUDIENCE);
  await downloadStagingProfiles(join(process.env.RUNNER_TEMP, 'foodhome-testflight'));
}

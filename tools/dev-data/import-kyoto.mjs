import { readFile, writeFile, mkdir, rmdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { randomBytes, randomUUID, createHash } from 'node:crypto';
import bcrypt from 'bcryptjs';

const root = fileURLToPath(new URL('../../', import.meta.url));
const data = JSON.parse(await readFile(new URL('./kyoto.json', import.meta.url), 'utf8'));
const origin = new URL(process.env.TRIPEE_API_ORIGIN || 'http://127.0.0.1:8080');
if (
  !['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname) ||
  origin.protocol !== 'http:' ||
  origin.username ||
  origin.password
)
  throw Error('개발용 등록은 로컬 HTTP 백엔드에서만 실행함.');
if (!existsSync(join(root, 'infra/.env'))) throw Error('infra/.env와 개발 DB를 먼저 준비해야 함.');
const archive = join(root, '.local-archive');
await mkdir(archive, { recursive: true });
const receiptPath = join(archive, 'kyoto-import-result.json');
const lock = join(archive, 'kyoto-import.lock');
await mkdir(lock).catch(() => {
  throw Error('다른 교토 등록 작업이 실행 중이거나 이전 잠금이 남아 있음.');
});

// SQL은 로컬 Compose의 사전·개발 계정 준비와 읽기 확인에만 사용함.
// 도시·사진·출처·초안·게시·감사 이력은 실제 관리자 API를 거침.
const literal = (value) => `'${String(value).replaceAll("'", "''")}'`;
function sql(statement) {
  const result = spawnSync(
    'docker',
    [
      'compose',
      '-f',
      'infra/compose.yaml',
      '--env-file',
      'infra/.env',
      'exec',
      '-T',
      'db',
      'sh',
      '-c',
      'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
    ],
    { cwd: root, input: statement, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 },
  );
  if (result.error || result.status !== 0)
    throw Error('개발 DB 조회·설정에 실패함. Docker 상태와 DB 설정을 확인해야 함.');
  return result.stdout.trim();
}
const rows = (statement) =>
  JSON.parse(sql(`SELECT coalesce(json_agg(q), '[]') FROM (${statement}) q;`));
const cookies = new Map();
let csrf, lease, cityId;
async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(new URL(`/api/v1${path}`, origin), {
    method,
    redirect: 'error',
    signal: AbortSignal.timeout(30000),
    headers: {
      ...(cookies.size ? { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') } : {}),
      ...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
      ...(lease ? { 'X-Edit-Lock-Token': lease } : {}),
      ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body ? (body instanceof FormData ? body : JSON.stringify(body)) : undefined,
  });
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';')[0],
      at = pair.indexOf('=');
    cookies.set(pair.slice(0, at), pair.slice(at + 1));
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw Error(`${method} ${path}: HTTP ${response.status} ${error.code || ''}`);
  }
  return {
    data: response.status === 204 ? null : await response.json(),
    etag: response.headers.get('etag'),
  };
}

try {
  await api('/cities');
  const receipt = existsSync(receiptPath) ? JSON.parse(await readFile(receiptPath, 'utf8')) : null;
  if (receipt && !/^\d+$/.test(receipt.cityId)) throw Error('로컬 등록 기록의 도시 ID가 잘못됨.');
  const existing =
    rows(`SELECT DISTINCT c.id::text, c.visibility, c.published_revision_id::text, r.updated_by::text, a.login_id FROM tripee.city c
    JOIN tripee.country k ON k.id=c.country_id JOIN tripee.city_revision r ON r.id=coalesce(c.draft_revision_id,c.published_revision_id)
    JOIN tripee.admin_account a ON a.id=r.updated_by WHERE k.code='JP' AND ${receipt ? `c.id=${literal(receipt.cityId)}` : "(r.name_ko='교토' OR lower(r.name_en)='kyoto')"}`);
  if (receipt && !existing.length)
    throw Error(
      '이전에 등록한 도시가 DB에 없음. 삭제·DB 교체 여부를 확인해야 함. 자동 재생성하지 않음.',
    );
  if (existing.length > 1)
    throw Error('교토가 여러 건 있어 자동 등록을 중단함. 기존 데이터를 먼저 확인해야 함.');
  if (existing[0]?.visibility === 'PUBLIC') {
    const { data: current } = await api(`/cities/${existing[0].id}`);
    if (current.country.code !== 'JP' || current.id !== existing[0].id)
      throw Error('백엔드와 Compose DB의 조회 결과가 다름.');
    for (const photo of current.photos) {
      const response = await fetch(new URL(photo.imageUrl, origin));
      if (!response.ok)
        throw Error('등록된 교토의 사진 파일을 확인해야 함. 기존 게시 데이터는 변경하지 않음.');
      await response.body?.cancel();
    }
    await writeFile(receiptPath, JSON.stringify({ cityId: current.id }));
    console.log(`교토 ${current.id}는 이미 게시돼 있음. 기존 콘텐츠·이력을 변경하지 않았음.`);
  } else {
    const loginId = 'dev-kyoto-import';
    if (existing[0]?.published_revision_id)
      throw Error('게시 이력이 있는 도시의 비공개·준비 상태를 자동 변경하지 않음.');
    if (existing[0] && existing[0].login_id !== loginId)
      throw Error('다른 관리자가 작성한 교토 초안은 자동으로 덮어쓰지 않음.');
    const credentialsPath = join(archive, 'kyoto-import-admin.json');
    let credentials;
    if (existsSync(credentialsPath))
      credentials = JSON.parse(await readFile(credentialsPath, 'utf8'));
    else {
      if (rows(`SELECT id FROM tripee.admin_account WHERE login_id=${literal(loginId)}`).length)
        throw Error('등록용 계정은 있지만 로컬 자격 증명이 없음. 비밀번호를 임의 변경하지 않음.');
      credentials = { loginId, password: randomBytes(24).toString('base64url') };
      await writeFile(credentialsPath, JSON.stringify(credentials), { flag: 'wx', mode: 0o600 });
    }
    const hash = await bcrypt.hash(credentials.password, 12);
    sql(`BEGIN;
      INSERT INTO tripee.admin_account(login_id,password_hash,display_name) VALUES (${literal(loginId)},${literal(hash)},'개발 자료 등록') ON CONFLICT (login_id) DO NOTHING;
      INSERT INTO tripee.country(code,name_ko,name_en) VALUES ('JP','일본','Japan') ON CONFLICT (code) DO NOTHING;
      ${data.tags.map((tag) => `INSERT INTO tripee.tag(code,label_ko,label_en) VALUES (${literal(tag.code)},${literal(tag.labelKo)},${literal(tag.labelEn)}) ON CONFLICT (code) DO NOTHING;`).join('\n')}
      COMMIT;`);
    csrf = (await api('/admin/csrf')).data.token;
    await api('/admin/session', { method: 'POST', body: credentials });
    csrf = (await api('/admin/csrf')).data.token;
    const countryId = (await api('/admin/countries')).data.items.find((c) => c.code === 'JP').id;
    const dictionary = (await api('/admin/tags')).data.items;
    const tagIds = data.tags.map((tag) => {
      const found = dictionary.find((t) => t.code === tag.code && t.isActive);
      if (!found) throw Error('등록할 활성 태그가 없음.');
      return found.id;
    });
    cityId =
      existing[0]?.id ||
      (
        await api('/admin/cities', {
          method: 'POST',
          body: {
            countryId,
            nameKo: data.nameKo,
            nameEn: data.nameEn,
            coordinates: data.coordinates,
            tagline: data.tagline,
          },
        })
      ).data.city.id;
    // 이름을 수정하더라도 다음 실행에서 같은 도시를 식별함. 삭제된 도시는 자동 복구하지 않음.
    await writeFile(receiptPath, JSON.stringify({ cityId }));
    const path = `/admin/cities/${cityId}`;
    lease = (
      await api(`${path}/edit-lock`, { method: 'POST', body: { editorInstanceId: randomUUID() } })
    ).data.leaseToken;
    const assets = (await api(`${path}/photo-assets`)).data.items;
    let asset = assets.find((a) => a.sourceUrl === data.photo.sourceUrl);
    if (!asset) {
      const bytes = await readFile(resolve(root, data.photo.file));
      if (createHash('sha256').update(bytes).digest('hex') !== data.photo.sha256)
        throw Error('사진 파일이 검수 대상과 다름.');
      const form = new FormData();
      form.set('file', new Blob([bytes], { type: 'image/jpeg' }), 'kyoto-togetsukyo.jpg');
      const { sourceUrl, creatorName, licenseName, licenseUrl, usageNote } = data.photo;
      form.set(
        'metadata',
        new Blob(
          [
            JSON.stringify({
              sourceUrl,
              creatorName,
              licenseName,
              licenseUrl,
              usageNote,
              rightsConfirmed: true,
            }),
          ],
          { type: 'application/json' },
        ),
      );
      asset = (await api(`${path}/photo-assets`, { method: 'POST', body: form })).data;
    }
    const sourceIds = new Map();
    for (const source of data.sources) {
      const found = rows(
        `SELECT id::text FROM tripee.source_document WHERE url=${literal(source.url)} ORDER BY id LIMIT 1`,
      )[0];
      sourceIds.set(
        source.url,
        found?.id ||
          (await api('/admin/source-documents', { method: 'POST', body: source })).data.id,
      );
    }
    const current = await api(`${path}/draft`);
    // 서버 검토 시각은 이번 등록 시각이며 원문 검수일은 근거 메모에 별도로 남김.
    const saved = (
      await api(`${path}/draft`, {
        method: 'PUT',
        headers: { 'If-Match': current.etag },
        body: {
          nameKo: data.nameKo,
          nameEn: data.nameEn,
          coordinates: data.coordinates,
          tagline: data.tagline,
          tagIds,
          photos: [
            {
              photoAssetId: asset.id,
              displayOrder: 1,
              altText: data.photo.altText,
              caption: data.photo.caption,
            },
          ],
          contentItems: data.contentItems.map((item) => ({
            id: null,
            kind: item.kind,
            title: item.title,
            body: item.body,
            displayOrder: item.displayOrder,
            isSelected: item.isSelected,
            sources: item.sourceUrls.map((url) => ({
              sourceDocumentId: sourceIds.get(url),
              evidenceNote: item.evidenceNote,
              reviewAction: 'CONFIRM',
            })),
          })),
          safetyDraft: null,
        },
      })
    ).data;
    const expected = {
      expectedRevisionId: saved.revisionId,
      expectedEditVersion: saved.editVersion,
    };
    const preview = (await api(`${path}/draft/preview`, { method: 'POST', body: expected })).data;
    if (!preview.canPublish)
      throw Error(`게시 요건 미충족: ${preview.violations.map((v) => v.code).join(', ')}`);
    await api(`${path}/draft/publications`, { method: 'POST', body: expected });
    lease = null;
    const { data: published } = await api(`/cities/${cityId}`);
    console.log(
      `교토 등록 완료: cityId=${cityId}, 경험 ${published.experiences.length}개, 사진 ${published.photos.length}개, 안전정보 ${published.safety.reviewState}`,
    );
  }
} finally {
  if (lease && cityId)
    await api(`/admin/cities/${cityId}/edit-lock`, { method: 'DELETE' }).catch(() => {});
  if (csrf) await api('/admin/session', { method: 'DELETE' }).catch(() => {});
  await rmdir(lock);
}

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const base = new URL('/api/v1/', process.env.TRIPEE_API_ORIGIN || 'http://127.0.0.1:8080');
const spec = JSON.parse(
  await readFile(new URL('../../docs/backend/openapi-v1.json', import.meta.url)),
);
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
const specId = 'urn:tripee:live';
ajv.addSchema({ ...spec, $id: specId });

async function checkResponse(path, contractPath, expectedStatus) {
  const response = await fetch(new URL(path, base), { redirect: 'manual' });
  assert.equal(response.status, expectedStatus, path);
  assert.equal(response.headers.get('cache-control'), 'no-store', path);
  const mime = response.headers.get('content-type')?.split(';')[0];
  const expected = spec.paths[contractPath].get.responses[expectedStatus].content;
  assert.ok(Object.hasOwn(expected, mime), `Unexpected Content-Type for ${path}`);
  const pointer = contractPath.replaceAll('/', '~1');
  const mediaPointer = mime.replaceAll('/', '~1');
  const validate = ajv.compile({
    $ref: `${specId}#/paths/${pointer}/get/responses/${expectedStatus}/content/${mediaPointer}/schema`,
  });
  const body = await response.json();
  assert.ok(validate(body), ajv.errorsText(validate.errors));
  return body;
}

test('지도 목록과 상태별 첫 번째 도시 상세 응답이 공개 계약과 일치함', async (t) => {
  const { items } = await checkResponse('cities', '/cities', 200);
  for (const visibility of ['PUBLIC', 'PREPARING']) {
    const marker = items.find((item) => item.visibility === visibility);
    await t.test(visibility, { skip: marker ? false : '해당 상태의 도시가 없음' }, async () => {
      const detail = await checkResponse(`cities/${marker.id}`, '/cities/{cityId}', 200);
      assert.equal(detail.id, marker.id);
      assert.equal(detail.visibility, visibility);
    });
  }
});

test('검색과 활성 태그가 공개 계약과 일치함', async () => {
  await checkResponse('search?q=교토', '/search', 200);
  const { items } = await checkResponse('tags', '/tags', 200);
  assert.ok(items.every((tag) => tag.isActive));
});

test('잘못된 조회 조건의 오류가 계약과 일치함', async () => {
  const problem = await checkResponse('cities?countryCode=invalid', '/cities', 400);
  assert.equal(problem.code, 'INVALID_QUERY');
});

test('관리자 세션의 CSRF 발급과 익명 접근 오류가 계약과 일치함', async () => {
  await checkResponse('admin/csrf', '/admin/csrf', 200);
  const problem = await checkResponse('admin/session', '/admin/session', 401);
  assert.equal(problem.code, 'AUTH_REQUIRED');
});

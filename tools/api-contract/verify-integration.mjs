import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const spec = JSON.parse(await readFile(new URL('../../docs/openapi-v1.json', import.meta.url)));
const file = new URL('../../backend/build/contract/responses.jsonl', import.meta.url);
const raw = await readFile(file, 'utf8');
const responses = raw.trim().split('\n').filter(Boolean).map(JSON.parse);
assert.ok(
  responses.length >= 50,
  '통합 응답이 충분하지 않습니다. postgresContainerTest를 실행하세요.',
);
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ ...spec, $id: 'urn:tripee:integration' });
const seen = new Set();
const failures = [];
const escape = (value) => value.replaceAll('~', '~0').replaceAll('/', '~1');
for (const response of responses) {
  const path = Object.keys(spec.paths).find((candidate) => {
    const parts = candidate.split('/');
    const actual = response.path.split('/');
    return (
      parts.length === actual.length &&
      parts.every((p, i) => /^\{[^}]+\}$/.test(p) || p === actual[i])
    );
  });
  const label = `${response.method.toUpperCase()} ${response.path} ${response.status}`;
  try {
    assert.ok(path, '명세에 없는 경로');
    const operation = spec.paths[path][response.method];
    assert.ok(operation, '명세에 없는 메서드');
    const contract = operation.responses[response.status];
    assert.ok(contract, '명세에 없는 상태 코드');
    assert.equal(response.headers['cache-control'], 'no-store', 'Cache-Control');
    for (const header of Object.keys(contract.headers ?? {})) {
      assert.ok(response.headers[header.toLowerCase()], `${header} 누락`);
    }
    const mime = response.contentType?.split(';')[0];
    if (contract.content) {
      assert.ok(contract.content[mime], `명세에 없는 Content-Type: ${mime}`);
      if (mime.includes('json')) {
        const pointer = `#/paths/${escape(path)}/${response.method}/responses/${response.status}/content/${escape(mime)}/schema`;
        const validate = ajv.compile({ $ref: `urn:tripee:integration${pointer}` });
        assert.ok(validate(response.body), ajv.errorsText(validate.errors, { separator: '; ' }));
      }
    } else assert.equal(response.body, undefined, '본문이 없는 응답에 JSON이 포함됨');
    seen.add(`${response.method} ${path} ${response.status}`);
  } catch (error) {
    failures.push(`${label}: ${error.message}`);
  }
}
for (const required of [
  'get /cities 200',
  'get /tags 200',
  'get /search 200',
  'get /cities 400',
  'get /admin/session 401',
  'post /admin/cities 201',
  'post /admin/cities 415',
  'post /admin/cities/{cityId}/photo-assets 400',
  'post /admin/cities/{cityId}/photo-assets 201',
  'put /admin/cities/{cityId}/draft 200',
  'put /admin/cities/{cityId}/draft 412',
  'post /admin/cities/{cityId}/draft/publications 422',
  'post /admin/cities/{cityId}/draft/publications 201',
  'get /admin/cities/{cityId}/revisions/{revisionId} 200',
  'get /admin/audit-events 200',
  'delete /admin/cities/{cityId} 204',
])
  if (!seen.has(required)) failures.push(`필수 검증 누락: ${required}`);
if (failures.length) {
  for (const failure of failures) console.error(failure);
  process.exitCode = 1;
} else
  console.log(`실제 응답 ${responses.length}건, 경로·메서드·상태 ${seen.size}종 계약 검증 통과`);

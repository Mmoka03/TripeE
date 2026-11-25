import { readFile, writeFile } from 'node:fs/promises';
import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import assert from 'node:assert/strict';

const specUrl = new URL('../../docs/backend/openapi-v1.json', import.meta.url);
const spec = JSON.parse(await readFile(specUrl, 'utf8'));
await SwaggerParser.validate(structuredClone(spec));
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema({ $id: 'urn:tripee:contract', components: spec.components });
const validators = Object.fromEntries(
  Object.keys(spec.components.schemas).map((name) => [
    name,
    ajv.compile({ $ref: 'urn:tripee:contract#/components/schemas/' + name }),
  ]),
);
const results = [];
const check = (name, fn) => {
  try {
    fn();
    results.push({ name, ok: true });
  } catch (e) {
    results.push({ name, ok: false, message: e.message });
  }
};
const dataCheck = (name, schema, data, expected) =>
  check(name, () =>
    assert.equal(
      Boolean(validators[schema](data)),
      expected,
      JSON.stringify(validators[schema].errors),
    ),
  );
const clone = (x) => structuredClone(x);
const now = '2026-09-28T03:00:00Z';
const sourceWrite = { sourceDocumentId: '1', evidenceNote: null, reviewAction: 'KEEP' };
const content = {
  id: null,
  kind: 'EXPERIENCE',
  title: null,
  body: null,
  displayOrder: 1,
  isSelected: false,
  sources: [sourceWrite],
};
const blankDraft = {
  nameKo: null,
  nameEn: null,
  coordinates: null,
  tagline: null,
  tagIds: [],
  photos: [],
  contentItems: [],
  safetyDraft: null,
};
const readyDraft = {
  ...blankDraft,
  nameKo: '교토',
  nameEn: 'Kyoto',
  coordinates: { latitude: 35, longitude: 135 },
  tagline: '역사 여행',
  tagIds: ['1'],
  photos: [{ photoAssetId: '1', displayOrder: 1, altText: '교토 거리', caption: null }],
  contentItems: [
    {
      ...content,
      title: '시장 탐방',
      body: '자료로 확인한 경험',
      isSelected: true,
      sources: [
        {
          sourceDocumentId: '1',
          evidenceNote: '제공한 자료에서 확인한 내용',
          reviewAction: 'CONFIRM',
        },
      ],
    },
  ],
};
const country = { id: '1', code: 'JP', nameKo: '일본', nameEn: 'Japan' };
const preparing = {
  id: '1',
  country,
  nameKo: '교토',
  nameEn: 'Kyoto',
  coordinates: { latitude: 35, longitude: 135 },
  visibility: 'PREPARING',
  message: '도시 정보를 준비 중이에요.',
};
const publicSource = {
  sourceDocumentId: '1',
  url: 'https://example.com/source',
  title: '검증용 자료',
  publisher: null,
  checkedAt: now,
};
const publicContent = {
  id: '1',
  kind: 'EXPERIENCE',
  title: '시장 탐방',
  body: '예제 본문',
  displayOrder: 1,
  sources: [publicSource],
};
const publicCity = {
  id: '1',
  country,
  nameKo: '교토',
  nameEn: 'Kyoto',
  coordinates: { latitude: 35, longitude: 135 },
  visibility: 'PUBLIC',
  revisionId: '101',
  tagline: '역사 여행',
  tags: [],
  photos: [
    {
      assetId: '1',
      imageUrl: '/api/v1/media/photo-assets/1',
      displayOrder: 1,
      altText: '예제 사진',
      caption: null,
      sourceUrl: 'https://example.com/photo',
      creatorName: null,
      licenseName: '예제',
      licenseUrl: null,
    },
  ],
  experiences: [publicContent],
  visitPeriods: [{ ...publicContent, kind: 'VISIT_PERIOD' }],
  arrival: [{ ...publicContent, kind: 'ARRIVAL' }],
  localTransport: [{ ...publicContent, kind: 'LOCAL_TRANSPORT' }],
  considerations: [],
  safety: { revisionId: '201', reviewState: 'UNVERIFIED', items: [], publishedAt: now },
  publishedAt: now,
};
const safety = {
  kind: 'OFFICIAL_ADVISORY',
  assessment: 'ISSUED',
  officialLevel: 'LEVEL_1',
  scopeType: 'CITY',
  originalScopeText: '예제 적용 범위',
  cityCoverage: 'WHOLE_CITY',
  cityScopeNote: '도시 전역',
  title: '예제 경보',
  body: '실제 경보가 아닌 테스트',
  displayOrder: 1,
  effectiveFrom: null,
  effectiveUntil: null,
  sources: [sourceWrite],
};

dataCheck('미완성 초안 허용', 'DraftWrite', blankDraft, true);
dataCheck('편집 가능한 초안 예제', 'DraftWrite', readyDraft, true);
let d = clone(blankDraft);
delete d.photos;
dataCheck('전체 저장 배열 누락 차단', 'DraftWrite', d, false);
d = clone(blankDraft);
d.tagIds = ['1', '1'];
dataCheck('중복 태그 차단', 'DraftWrite', d, false);
d = clone(blankDraft);
d.tagline = ' ';
dataCheck('공백 소개 거부', 'DraftWrite', d, false);
d = clone(blankDraft);
d.publishedAt = now;
dataCheck('클라이언트 게시시각 주입 거부', 'DraftWrite', d, false);
d = clone(blankDraft);
d.coordinates = { latitude: 91, longitude: 135 };
dataCheck('잘못된 위도 거부', 'DraftWrite', d, false);
d = clone(blankDraft);
d.coordinates = { latitude: 35 };
dataCheck('좌표 쌍 필요', 'DraftWrite', d, false);
dataCheck('본문 3000자 허용', 'ContentWrite', { ...content, body: '가'.repeat(3000) }, true);
dataCheck('본문 3001자 거부', 'ContentWrite', { ...content, body: '가'.repeat(3001) }, false);
dataCheck(
  '저장 후보는 3개 초과 가능',
  'DraftWrite',
  {
    ...blankDraft,
    contentItems: [1, 2, 3, 4].map((displayOrder) => ({ ...content, displayOrder })),
  },
  true,
);
dataCheck('순서 0 거부', 'ContentWrite', { ...content, displayOrder: 0 }, false);
dataCheck(
  '출처 재확인 명시',
  'SourceLinkWrite',
  { ...sourceWrite, reviewAction: 'CONFIRM', evidenceNote: '확인 메모' },
  true,
);
dataCheck('출처 확인시각 주입 차단', 'SourceLinkWrite', { ...sourceWrite, checkedAt: now }, false);
dataCheck('관리자 지정 주입 차단', 'SourceLinkWrite', { ...sourceWrite, checkedBy: '1' }, false);
dataCheck('문자열 ID 유지', 'Id', '9007199254740993', true);
dataCheck('숫자 ID 전송 차단', 'Id', 9007199254740992, false);
dataCheck('음수 ID 차단', 'Id', '-1', false);
dataCheck('선행 0 ID 차단', 'Id', '01', false);
dataCheck('준비 중 공개 DTO', 'CityDetail', preparing, true);
dataCheck('준비 중 초안 노출 차단', 'CityDetail', { ...preparing, draft: readyDraft }, false);
dataCheck('공개 도시 DTO', 'CityDetail', publicCity, true);
d = clone(publicCity);
d.experiences = [1, 2, 3, 4].map((i) => ({ ...publicContent, id: String(i) }));
dataCheck('공개 경험 4개 거부', 'CityDetail', d, false);
d = clone(publicCity);
d.photos = [];
dataCheck('공개 사진 최소 1개', 'CityDetail', d, false);
d = clone(publicCity);
d.photos[0].altText = null;
dataCheck('공개 대체 텍스트 필수', 'CityDetail', d, false);
d = clone(publicCity);
d.leaseToken = 'secret';
dataCheck('공개 응답 잠금토큰 차단', 'CityDetail', d, false);
dataCheck('안전 공식 발령 조합', 'SafetyItemWrite', safety, true);
dataCheck('안전 특별 단계 허용', 'SafetyItemWrite', { ...safety, officialLevel: 'SPECIAL' }, true);
dataCheck('발령 단계 누락 거부', 'SafetyItemWrite', { ...safety, officialLevel: null }, false);
dataCheck(
  '미발령 단계 혼합 거부',
  'SafetyItemWrite',
  { ...safety, assessment: 'NOT_ISSUED' },
  false,
);
dataCheck(
  '현지 주의 조합',
  'SafetyItemWrite',
  { ...safety, kind: 'LOCAL_CAUTION', assessment: 'CAUTION', officialLevel: null },
  true,
);
dataCheck(
  '안전 본문 3001자 거부',
  'SafetyItemWrite',
  { ...safety, body: '가'.repeat(3001) },
  false,
);
dataCheck(
  '독립 안전정보 빈 상태',
  'SafetyView',
  { revisionId: null, reviewState: 'UNVERIFIED', items: [], publishedAt: null, publishedBy: null },
  true,
);
dataCheck(
  '재검토 기반 버전 명시',
  'SafetyReviewRequest',
  {
    expectedRevisionId: '101',
    expectedEditVersion: '1',
    reviewedPublicSafetyRevisionId: '202',
    safety: { reviewState: 'PARTIAL', items: [safety] },
  },
  true,
);
dataCheck(
  '기반 없는 재검토 거부',
  'SafetyReviewRequest',
  {
    expectedRevisionId: '101',
    expectedEditVersion: '1',
    safety: { reviewState: 'PARTIAL', items: [safety] },
  },
  false,
);
dataCheck('빈 검색 결과 정상', 'SearchResponse', { items: [] }, true);
dataCheck(
  '태그 영문명 포함',
  'Tag',
  { id: '1', code: 'HISTORY', labelKo: '역사', labelEn: 'History', isActive: true },
  true,
);
dataCheck(
  '태그 영문명 누락 거부',
  'Tag',
  { id: '1', code: 'HISTORY', labelKo: '역사', isActive: true },
  false,
);
dataCheck(
  'Problem Details 오류 응답',
  'Problem',
  {
    type: 'about:blank',
    title: 'Conflict',
    status: 409,
    detail: '안전정보를 다시 검토해주세요.',
    instance: '/api/v1/admin/cities/1/draft/publications',
    code: 'SAFETY_CHANGED',
    traceId: 'test',
  },
  true,
);
dataCheck(
  'AI 자료 포함 요청',
  'AiSuggestionRequest',
  {
    expectedRevisionId: '101',
    expectedEditVersion: '0',
    sources: [{ sourceDocumentId: '1', text: '입력 자료' }],
  },
  true,
);
dataCheck(
  'AI 자료 없음 거부',
  'AiSuggestionRequest',
  { expectedRevisionId: '101', expectedEditVersion: '0', sources: [] },
  false,
);
dataCheck(
  'AI 출처 6개 거부',
  'AiSuggestionRequest',
  {
    expectedRevisionId: '101',
    expectedEditVersion: '0',
    sources: Array.from({ length: 6 }, () => ({ sourceDocumentId: '1', text: '본문' })),
  },
  false,
);
dataCheck(
  'AI 결과 자동저장 금지 계약',
  'AiSuggestionResponse',
  {
    basedOnRevisionId: '101',
    basedOnEditVersion: '0',
    persisted: true,
    experiences: [],
    warnings: [],
  },
  false,
);

const operations = [];
for (const [path, methods] of Object.entries(spec.paths))
  for (const [method, operation] of Object.entries(methods)) {
    operations.push({ path, method, operation });
    const headers = operation.parameters?.filter((p) => p.in === 'header').map((p) => p.name) ?? [];
    if (
      path.startsWith('/admin/') &&
      !(path === '/admin/csrf' || (path === '/admin/session' && method === 'post'))
    )
      check(`${method} ${path} 관리자 인증`, () =>
        assert.ok(operation.security.some((s) => s.AdminSession)),
      );
    if (path.startsWith('/admin/') && !['get', 'head'].includes(method))
      check(`${method} ${path} CSRF 헤더`, () => assert.ok(headers.includes('X-CSRF-TOKEN')));
    for (const name of [...path.matchAll(/\{([^}]+)\}/g)].map((m) => m[1]))
      check(`${method} ${path} 경로 매개변수 ${name}`, () =>
        assert.ok(
          operation.parameters.some((p) => p.in === 'path' && p.name === name && p.required),
        ),
      );
  }
check('operationId 중복 없음', () =>
  assert.equal(new Set(operations.map((x) => x.operation.operationId)).size, operations.length),
);
check('안전 독립 게시 잠금 예외', () =>
  assert.ok(
    !spec.paths['/admin/cities/{cityId}/safety'].put.parameters.some(
      (p) => p.name === 'X-Edit-Lock-Token',
    ),
  ),
);
check('안전 독립 게시 If-Match', () =>
  assert.ok(
    spec.paths['/admin/cities/{cityId}/safety'].put.parameters.some(
      (p) => p.name === 'If-Match' && p.required,
    ),
  ),
);
check('초안 조회 타인 잠금 차단 계약', () =>
  assert.ok(
    spec.paths['/admin/cities/{cityId}/draft'].get.parameters.some(
      (p) => p.name === 'X-Edit-Lock-Token' && p.required,
    ),
  ),
);
check('초안 전체 저장 조건부 요청', () =>
  assert.ok(
    spec.paths['/admin/cities/{cityId}/draft'].put.responses['412'] &&
      spec.paths['/admin/cities/{cityId}/draft'].put.responses['428'],
  ),
);
check('삭제 감사 조회 독립 경로', () => assert.ok(spec.paths['/admin/audit-events/{eventId}'].get));
check('도시 삭제 If-Match와 잠금', () =>
  assert.ok(
    ['If-Match', 'X-Edit-Lock-Token'].every((n) =>
      spec.paths['/admin/cities/{cityId}'].delete.parameters.some((p) => p.name === n),
    ),
  ),
);
check('파일 업로드 JSON 메타데이터', () =>
  assert.equal(
    spec.paths['/admin/cities/{cityId}/photo-assets'].post.requestBody.content[
      'multipart/form-data'
    ].encoding.metadata.contentType,
    'application/json',
  ),
);
check('GET에 요청 본문 없음', () =>
  assert.ok(operations.filter((o) => o.method === 'get').every((o) => !o.operation.requestBody)),
);
check('활동 시각 클라이언트 입력 거부', () => {
  const validate = ajv.compile(
    spec.paths['/admin/session/activity'].post.requestBody.content['application/json'].schema,
  );
  assert.equal(validate({ event: 'INPUT', occurredAt: now }), false);
});

check('Location 헤더는 생성 응답에만 명시됨', () => {
  for (const [path, operations] of Object.entries(spec.paths)) {
    for (const [method, operation] of Object.entries(operations)) {
      for (const [status, response] of Object.entries(operation.responses ?? {})) {
        if (response.headers?.Location) assert.equal(status, '201', `${method} ${path} ${status}`);
      }
    }
  }
});

const report = {
  openapi: '3.1.1',
  paths: Object.keys(spec.paths).length,
  operations: operations.length,
  schemas: Object.keys(validators).length,
  total: results.length,
  passed: results.filter((r) => r.ok).length,
  results,
};
await writeFile(new URL('./results.json', import.meta.url), JSON.stringify(report, null, 2) + '\n');
for (const r of results.filter((r) => !r.ok)) console.log(JSON.stringify(r));
console.log(
  JSON.stringify({
    paths: report.paths,
    operations: report.operations,
    schemas: report.schemas,
    total: report.total,
    passed: report.passed,
  }),
);
if (report.passed !== report.total) process.exitCode = 1;

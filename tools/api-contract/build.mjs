import { writeFile } from 'node:fs/promises';

const ref = (name) => ({ $ref: '#/components/schemas/' + name });
const text = (maxLength, extra = {}) => ({
  type: 'string',
  ...(maxLength ? { maxLength } : {}),
  ...extra,
});
const nullable = (schema) => ({ anyOf: [schema, { type: 'null' }] });
const arr = (items, extra = {}) => ({ type: 'array', items, ...extra });
const obj = (properties, optional = []) => ({
  type: 'object',
  properties,
  required: Object.keys(properties).filter((k) => !optional.includes(k)),
  additionalProperties: false,
});
const en = (...values) => ({ type: 'string', enum: values });
const bool = { type: 'boolean' };
const integer = (minimum = 0, maximum) => ({
  type: 'integer',
  minimum,
  ...(maximum === undefined ? {} : { maximum }),
});
const instant = { type: 'string', format: 'date-time' };
const uuid = { type: 'string', format: 'uuid' };
const id = text(19, {
  pattern: '^[1-9][0-9]{0,18}$',
  description:
    'PostgreSQL bigint ID encoded as a decimal string; server also checks signed bigint range.',
});
const version = text(19, {
  pattern: '^(0|[1-9][0-9]{0,18})$',
  description: 'Nonnegative bigint edit version encoded as a string.',
});
const nonblank = (max) => text(max, { minLength: 1, pattern: '\\S' });
const url = text(4096, { format: 'uri', pattern: '^https?://\\S+$' });
const kind = en('EXPERIENCE', 'CONSIDERATION', 'VISIT_PERIOD', 'ARRIVAL', 'LOCAL_TRANSPORT');
const visibility = en('HIDDEN', 'PREPARING', 'PUBLIC');
const review = en('UNVERIFIED', 'PARTIAL', 'CHECKED');
const schemas = {};
const add = (name, schema) => ((schemas[name] = schema), ref(name));
add('Id', id);
add('EditVersion', version);
add('Actor', obj({ id: ref('Id'), displayName: nonblank(80) }));
add(
  'Country',
  obj({
    id: ref('Id'),
    code: text(2, { pattern: '^[A-Z]{2}$' }),
    nameKo: nonblank(150),
    nameEn: nonblank(150),
  }),
);
add(
  'Tag',
  obj({
    id: ref('Id'),
    code: nonblank(50),
    labelKo: nonblank(64),
    labelEn: nonblank(64),
    isActive: bool,
  }),
);
add(
  'Coordinates',
  obj({
    latitude: { type: 'number', minimum: -90, maximum: 90 },
    longitude: { type: 'number', minimum: -180, maximum: 180 },
  }),
);
add('Violation', obj({ path: text(), code: text(), message: text() }));
add(
  'Problem',
  obj(
    {
      type: text(undefined, { format: 'uri-reference' }),
      title: text(),
      status: integer(400, 599),
      detail: text(),
      instance: text(undefined, { format: 'uri-reference' }),
      code: text(),
      traceId: text(),
      violations: arr(ref('Violation')),
      context: { type: 'object', additionalProperties: true },
    },
    ['violations', 'context'],
  ),
);
add(
  'SourceDocument',
  obj({
    id: ref('Id'),
    url,
    title: nonblank(500),
    publisher: nullable(nonblank(200)),
    createdAt: instant,
  }),
);
add('SourceDocumentWrite', obj({ url, title: nonblank(500), publisher: nullable(nonblank(200)) }));
add(
  'SourceLinkWrite',
  obj({
    sourceDocumentId: ref('Id'),
    evidenceNote: nullable(nonblank(4000)),
    reviewAction: en('KEEP', 'CONFIRM'),
  }),
);
add(
  'SourceLink',
  obj({
    sourceDocument: ref('SourceDocument'),
    evidenceNote: nullable(nonblank(4000)),
    checkedAt: nullable(instant),
    checkedBy: nullable(ref('Actor')),
  }),
);
add(
  'PublicSource',
  obj({
    sourceDocumentId: ref('Id'),
    url,
    title: nonblank(500),
    publisher: nullable(nonblank(200)),
    checkedAt: instant,
  }),
);
add(
  'ContentWrite',
  obj({
    id: nullable(ref('Id')),
    kind,
    title: nullable(nonblank(200)),
    body: nullable(nonblank(3000)),
    displayOrder: integer(1, 2147483647),
    isSelected: bool,
    sources: arr(ref('SourceLinkWrite')),
  }),
);
add(
  'ContentItem',
  obj({
    id: ref('Id'),
    kind,
    title: nullable(nonblank(200)),
    body: nullable(nonblank(3000)),
    displayOrder: integer(1, 2147483647),
    isSelected: bool,
    sources: arr(ref('SourceLink')),
  }),
);
add(
  'PublicContent',
  obj({
    id: ref('Id'),
    kind,
    title: nonblank(200),
    body: nonblank(3000),
    displayOrder: integer(1),
    sources: arr(ref('PublicSource'), { minItems: 1 }),
  }),
);
const safetyFields = {
  kind: en('OFFICIAL_ADVISORY', 'LOCAL_CAUTION'),
  assessment: en('ISSUED', 'NOT_ISSUED', 'CAUTION'),
  officialLevel: nullable(en('LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'LEVEL_4', 'SPECIAL')),
  scopeType: nullable(en('COUNTRY_WIDE', 'REGION', 'CITY', 'SPECIFIC_PLACE')),
  originalScopeText: nullable(nonblank(4000)),
  cityCoverage: nullable(en('WHOLE_CITY', 'PART_OF_CITY')),
  cityScopeNote: nullable(nonblank(4000)),
  title: nullable(nonblank(200)),
  body: nullable(nonblank(3000)),
  displayOrder: integer(1, 2147483647),
  effectiveFrom: nullable(instant),
  effectiveUntil: nullable(instant),
};
const safetyCombination = [
  {
    properties: {
      kind: { const: 'OFFICIAL_ADVISORY' },
      assessment: { const: 'ISSUED' },
      officialLevel: { type: 'string' },
    },
  },
  {
    properties: {
      kind: { const: 'OFFICIAL_ADVISORY' },
      assessment: { const: 'NOT_ISSUED' },
      officialLevel: { type: 'null' },
    },
  },
  {
    properties: {
      kind: { const: 'LOCAL_CAUTION' },
      assessment: { const: 'CAUTION' },
      officialLevel: { type: 'null' },
    },
  },
];
add('SafetyItemWrite', {
  ...obj({ ...safetyFields, sources: arr(ref('SourceLinkWrite')) }),
  oneOf: safetyCombination,
});
add('SafetyItem', {
  ...obj({ id: ref('Id'), ...safetyFields, sources: arr(ref('SourceLink')) }),
  oneOf: safetyCombination,
});
add('SafetyWrite', obj({ reviewState: review, items: arr(ref('SafetyItemWrite')) }));
add(
  'SafetyView',
  obj({
    revisionId: nullable(ref('Id')),
    reviewState: review,
    items: arr(ref('SafetyItem')),
    publishedAt: nullable(instant),
    publishedBy: nullable(ref('Actor')),
  }),
);
add(
  'SafetyDraft',
  obj({ revisionId: ref('Id'), reviewState: review, items: arr(ref('SafetyItem')) }),
);
add('PublicSafetyItem', {
  ...obj({
    id: ref('Id'),
    ...safetyFields,
    scopeType: en('COUNTRY_WIDE', 'REGION', 'CITY', 'SPECIFIC_PLACE'),
    originalScopeText: nonblank(4000),
    cityCoverage: en('WHOLE_CITY', 'PART_OF_CITY'),
    cityScopeNote: nonblank(4000),
    title: nonblank(200),
    body: nonblank(3000),
    temporalStatus: en('CURRENT', 'UPCOMING', 'EXPIRED'),
    sources: arr(ref('PublicSource'), { minItems: 1 }),
  }),
  oneOf: safetyCombination,
});
add(
  'PublicSafety',
  obj({
    revisionId: ref('Id'),
    reviewState: review,
    items: arr(ref('PublicSafetyItem')),
    publishedAt: instant,
  }),
);
add(
  'PhotoMetadataWrite',
  obj({
    sourceUrl: url,
    creatorName: nullable(nonblank(200)),
    licenseName: nullable(nonblank(200)),
    licenseUrl: nullable(url),
    usageNote: nullable(nonblank(2000)),
    rightsConfirmed: bool,
  }),
);
add(
  'PhotoAsset',
  obj({
    id: ref('Id'),
    cityId: ref('Id'),
    imageUrl: text(),
    sourceUrl: url,
    creatorName: nullable(nonblank(200)),
    licenseName: nullable(nonblank(200)),
    licenseUrl: nullable(url),
    usageNote: nullable(nonblank(2000)),
    rightsCheckedAt: nullable(instant),
  }),
);
add(
  'PhotoLinkWrite',
  obj({
    photoAssetId: ref('Id'),
    displayOrder: integer(1, 2147483647),
    altText: nullable(nonblank(500)),
    caption: nullable(nonblank(1000)),
  }),
);
add(
  'DraftPhoto',
  obj({
    asset: ref('PhotoAsset'),
    displayOrder: integer(1, 2147483647),
    altText: nullable(nonblank(500)),
    caption: nullable(nonblank(1000)),
  }),
);
add(
  'PublicPhoto',
  obj({
    assetId: ref('Id'),
    imageUrl: text(),
    displayOrder: integer(1),
    altText: nonblank(500),
    caption: nullable(nonblank(1000)),
    sourceUrl: url,
    creatorName: nullable(nonblank(200)),
    licenseName: nullable(nonblank(200)),
    licenseUrl: nullable(url),
  }),
);
const editable = {
  nameKo: nullable(nonblank(150)),
  nameEn: nullable(nonblank(150)),
  coordinates: nullable(ref('Coordinates')),
  tagline: nullable(nonblank(200)),
};
add(
  'DraftWrite',
  obj({
    ...editable,
    tagIds: arr(ref('Id'), { uniqueItems: true }),
    photos: arr(ref('PhotoLinkWrite')),
    contentItems: arr(ref('ContentWrite')),
    safetyDraft: nullable(ref('SafetyWrite')),
  }),
);
add(
  'Draft',
  obj({
    cityId: ref('Id'),
    revisionId: ref('Id'),
    revisionNo: integer(1),
    editVersion: ref('EditVersion'),
    ...editable,
    tagIds: arr(ref('Id'), { uniqueItems: true }),
    photos: arr(ref('DraftPhoto')),
    contentItems: arr(ref('ContentItem')),
    baseSafetyRevisionId: nullable(ref('Id')),
    safetyDraft: nullable(ref('SafetyDraft')),
    updatedAt: instant,
    updatedBy: ref('Actor'),
  }),
);
add(
  'CitySummary',
  obj({
    id: ref('Id'),
    country: ref('Country'),
    nameKo: nonblank(150),
    nameEn: nonblank(150),
    coordinates: ref('Coordinates'),
    visibility: en('PREPARING', 'PUBLIC'),
  }),
);
add(
  'PreparingCity',
  obj({
    id: ref('Id'),
    country: ref('Country'),
    nameKo: nonblank(150),
    nameEn: nonblank(150),
    coordinates: ref('Coordinates'),
    visibility: { const: 'PREPARING' },
    message: text(),
  }),
);
add(
  'PublishedCity',
  obj({
    id: ref('Id'),
    country: ref('Country'),
    nameKo: nonblank(150),
    nameEn: nonblank(150),
    coordinates: ref('Coordinates'),
    visibility: { const: 'PUBLIC' },
    revisionId: ref('Id'),
    tagline: nonblank(200),
    tags: arr(ref('Tag')),
    photos: arr(ref('PublicPhoto'), { minItems: 1 }),
    experiences: arr(ref('PublicContent'), { minItems: 1, maxItems: 3 }),
    visitPeriods: arr(ref('PublicContent'), { minItems: 1 }),
    arrival: arr(ref('PublicContent'), { minItems: 1 }),
    localTransport: arr(ref('PublicContent'), { minItems: 1 }),
    considerations: arr(ref('PublicContent')),
    safety: ref('PublicSafety'),
    publishedAt: instant,
  }),
);
add('CityDetail', { oneOf: [ref('PreparingCity'), ref('PublishedCity')] });
add('CitySearchResult', obj({ type: { const: 'CITY' }, city: ref('CitySummary') }));
add(
  'CountrySearchResult',
  obj({ type: { const: 'COUNTRY' }, country: ref('Country'), visibleCityCount: integer(1) }),
);
add('SearchResult', { oneOf: [ref('CitySearchResult'), ref('CountrySearchResult')] });
add(
  'CityAdmin',
  obj({
    id: ref('Id'),
    country: ref('Country'),
    visibility,
    nameKo: nullable(nonblank(150)),
    nameEn: nullable(nonblank(150)),
    publishedRevisionId: nullable(ref('Id')),
    draftRevisionId: nullable(ref('Id')),
    draftEditVersion: nullable(ref('EditVersion')),
    publishedSafetyRevisionId: nullable(ref('Id')),
  }),
);
add('CityCreate', obj({ countryId: ref('Id'), ...editable }));
add('CityCreated', obj({ city: ref('CityAdmin'), draftRevisionId: ref('Id') }));
add(
  'LockView',
  obj({
    isLocked: bool,
    owner: nullable(ref('Actor')),
    editorInstanceId: nullable(uuid),
    expiresAt: nullable(instant),
    serverTime: instant,
  }),
);
add(
  'Lease',
  obj({
    cityId: ref('Id'),
    owner: ref('Actor'),
    editorInstanceId: uuid,
    leaseToken: nonblank(512),
    expiresAt: instant,
    serverTime: instant,
    heartbeatIntervalSeconds: { const: 60 },
    leaseDurationSeconds: { const: 300 },
  }),
);
add('Heartbeat', obj({ expiresAt: instant, serverTime: instant }));
add(
  'Session',
  obj({
    admin: ref('Actor'),
    lastActivityAt: instant,
    idleExpiresAt: instant,
    serverTime: instant,
  }),
);
add(
  'Preview',
  obj({
    cityId: ref('Id'),
    revisionId: ref('Id'),
    editVersion: ref('EditVersion'),
    effectiveSafety: ref('SafetyView'),
    draft: ref('Draft'),
    canPublish: bool,
    violations: arr(ref('Violation')),
  }),
);
add(
  'PublishRequest',
  obj({ expectedRevisionId: ref('Id'), expectedEditVersion: ref('EditVersion') }),
);
add(
  'SafetyReviewRequest',
  obj({
    expectedRevisionId: ref('Id'),
    expectedEditVersion: ref('EditVersion'),
    reviewedPublicSafetyRevisionId: nullable(ref('Id')),
    safety: ref('SafetyWrite'),
  }),
);
add(
  'AuditSummary',
  obj({
    id: ref('Id'),
    cityRefId: ref('Id'),
    revisionRefId: nullable(ref('Id')),
    safetyRevisionRefId: nullable(ref('Id')),
    actor: ref('Actor'),
    action: en(
      'CREATE_DRAFT',
      'UPDATE_DRAFT',
      'DISCARD_DRAFT',
      'PUBLISH',
      'SAFETY_PUBLISH',
      'UNPUBLISH',
      'SET_VISIBILITY',
      'DELETE',
    ),
    occurredAt: instant,
  }),
);
add(
  'AuditDetail',
  obj({
    ...schemas.AuditSummary.properties,
    snapshotSchemaVersion: integer(1),
    beforeData: nullable({ type: 'object', additionalProperties: true }),
    afterData: nullable({ type: 'object', additionalProperties: true }),
  }),
);
add(
  'HistoricalPublication',
  obj({
    cityId: ref('Id'),
    revisionId: ref('Id'),
    revisionNo: integer(1),
    publishedAt: instant,
    publishedBy: ref('Actor'),
    snapshot: {
      type: 'object',
      additionalProperties: true,
      description:
        'Versioned history projection: saved content, then-applicable safety, resolved source/photo/tag metadata. Never credentials.',
    },
  }),
);
add(
  'AiSuggestionRequest',
  obj({
    expectedRevisionId: ref('Id'),
    expectedEditVersion: ref('EditVersion'),
    sources: arr(obj({ sourceDocumentId: ref('Id'), text: nonblank(10000) }), {
      minItems: 1,
      maxItems: 5,
    }),
  }),
);
add(
  'AiSuggestion',
  obj({
    title: nonblank(200),
    body: nonblank(3000),
    sourceDocumentIds: arr(ref('Id'), { minItems: 1, uniqueItems: true }),
  }),
);
add(
  'AiSuggestionResponse',
  obj({
    basedOnRevisionId: ref('Id'),
    basedOnEditVersion: ref('EditVersion'),
    persisted: { const: false },
    experiences: arr(ref('AiSuggestion'), { maxItems: 5 }),
    warnings: arr(text()),
  }),
);
for (const [name, item] of [
  ['CityList', 'CitySummary'],
  ['CountryList', 'Country'],
  ['TagList', 'Tag'],
  ['PhotoAssetList', 'PhotoAsset'],
  ['SearchResponse', 'SearchResult'],
])
  add(name, obj({ items: arr(ref(item)) }));
for (const [name, item] of [
  ['AdminCityPage', 'CityAdmin'],
  ['SourcePage', 'SourceDocument'],
  ['AuditPage', 'AuditSummary'],
])
  add(name, obj({ items: arr(ref(item)), nextCursor: nullable(text()) }));

const hdr = (name, schema, description, required = true) => ({
  in: 'header',
  name,
  required,
  schema,
  description,
});
const q = (name, schema, description, required = false) => ({
  in: 'query',
  name,
  required,
  schema,
  description,
});
const csrf = hdr(
  'X-CSRF-TOKEN',
  text(),
  'Token obtained from /admin/csrf; mandatory for every state-changing admin request.',
);
const lease = hdr(
  'X-Edit-Lock-Token',
  text(),
  'Raw lease token for this authenticated editor. Never put in URLs/logs.',
);
const match = hdr(
  'If-Match',
  text(undefined, { pattern: '^"[^"\\r\\n]+"$' }),
  'Single strong ETag returned by GET of this exact resource. No wildcard or comma-separated list.',
);
const etagHeader = {
  ETag: {
    description: 'Strong opaque validator. Preserve quotes; never construct client-side.',
    schema: text(),
  },
};
const response = (schema, description = 'Success', etag = false) => ({
  description,
  ...(etag ? { headers: { ...etagHeader } } : {}),
  ...(schema
    ? {
        content: {
          'application/json': { schema: typeof schema === 'string' ? ref(schema) : schema },
        },
      }
    : {}),
});
const paths = {};
const httpErrors = {
  400: 'Malformed request',
  401: 'Authentication required or expired',
  403: 'Permission or CSRF failure',
  404: 'Resource unavailable',
  409: 'Workflow or lease conflict',
  412: 'Resource version changed',
  413: 'Payload too large',
  415: 'Unsupported media type',
  422: 'Validation failed',
  428: 'If-Match required',
  429: 'Rate limited',
  500: 'Unexpected failure',
  502: 'AI provider failure or invalid result',
  504: 'AI provider timeout',
};
const problemResponse = (code) => ({
  description: httpErrors[code],
  content: { 'application/problem+json': { schema: ref('Problem') } },
  ...(code === 429
    ? {
        headers: {
          'Retry-After': { schema: text(), description: 'Seconds until retry is allowed.' },
        },
      }
    : {}),
});
function op(path, method, operationId, summary, options = {}) {
  const {
    auth = false,
    lock = false,
    conditional = false,
    request,
    output,
    status = 200,
    description = '',
    etag = false,
    parameters = [],
    errors = [],
    media,
    location = false,
  } = options;
  const params = [...parameters];
  for (const m of path.matchAll(/\{([^}]+)\}/g))
    params.push({ in: 'path', name: m[1], required: true, schema: ref('Id') });
  if (auth && !['get', 'head'].includes(method)) params.push(csrf);
  if (lock) params.push(lease);
  if (conditional) params.push(match);
  const success = response(output, 'Success', etag);
  if (location) {
    success.headers ??= {};
    success.headers.Location = { schema: text(), description: 'Created resource URI.' };
  }
  if (media) success.content = media;
  const errorSet = new Set([
    400,
    ...(request ? [413, 415] : []),
    500,
    ...(auth ? [401, 403] : []),
    ...(lock ? [409] : []),
    ...(conditional ? [412, 428] : []),
    ...errors,
  ]);
  const value = {
    operationId,
    summary,
    description,
    tags: [auth ? 'Admin' : 'Public'],
    security: auth ? [{ AdminSession: [] }] : [],
    ...(params.length ? { parameters: params } : {}),
    responses: {
      [status]: success,
      ...Object.fromEntries([...errorSet].map((c) => [c, problemResponse(c)])),
    },
  };
  if (request)
    value.requestBody = {
      required: true,
      content: {
        'application/json': { schema: typeof request === 'string' ? ref(request) : request },
      },
    };
  paths[path] ??= {};
  paths[path][method] = value;
  return value;
}
const page = [
  q('cursor', text(), 'Opaque server cursor'),
  q('limit', integer(1, 100), 'Page size, default 20'),
];
op('/cities', 'get', 'listVisibleCities', '지도용 도시 목록', {
  output: 'CityList',
  parameters: [q('countryCode', text(2, { pattern: '^[A-Z]{2}$' }), 'Optional country filter')],
  description:
    'Returns all visible PUBLIC/PREPARING markers for the MVP; HIDDEN/deleted excluded. No silent pagination/truncation. Clustering is client-side.',
});
op('/cities/{cityId}', 'get', 'getCity', '도시 상세 또는 준비 중 안내', {
  output: 'CityDetail',
  errors: [404],
  description:
    'PUBLIC combines current general and current safety revisions. PREPARING returns only safe basic fields. HIDDEN/deleted return 404. Experience limit 3. No admin evidence notes or storage keys.',
});
op('/search', 'get', 'searchDestinations', '등록 도시·국가 검색', {
  output: 'SearchResponse',
  parameters: [
    q('q', nonblank(100), 'Trimmed Korean/English city/country search', true),
    q('limit', integer(1, 50), 'Default 10'),
  ],
  description:
    'Returns CITY/COUNTRY typed results from visible registered destinations only. Exact then prefix then substring; stable type/id tie-break. No external geocoding. Empty matches = items:[]. Country map extent comes from client country-boundary configuration.',
});
op('/tags', 'get', 'listPublicTags', '활성 태그 사전', {
  output: 'TagList',
  description:
    'All active tags. Tag filtering is not added to MVP merely because a dictionary exists.',
});
op('/media/photo-assets/{assetId}', 'get', 'getPhotoImage', '공개 또는 관리자용 사진', {
  errors: [404],
  media: {
    'image/jpeg': { schema: { type: 'string', format: 'binary' } },
    'image/png': { schema: { type: 'string', format: 'binary' } },
    'image/webp': { schema: { type: 'string', format: 'binary' } },
  },
  description:
    'Anonymous access only if asset is referenced by a PUBLIC city current revision. Authenticated active admin may view retained assets for editing/history. Otherwise 404. Deleted assets unavailable. Never expose filesystem paths.',
});
op('/admin/csrf', 'get', 'getCsrf', 'CSRF 토큰 발급', {
  output: obj({ headerName: { const: 'X-CSRF-TOKEN' }, token: nonblank(512) }),
  description:
    'Accessible before login. Response no-store. Login rotates session; fetch a fresh token after login/logout.',
});
const login = op('/admin/session', 'post', 'loginAdmin', '관리자 로그인', {
  request: obj({ loginId: nonblank(64), password: nonblank(1024) }),
  output: 'Session',
  errors: [401, 403, 429],
  description:
    'CSRF protected even before authentication. Rotate server session ID; Set-Cookie session. Generic credentials error. No signup API.',
});
login.parameters = [csrf];
op('/admin/session', 'get', 'getAdminSession', '현재 관리자 세션', {
  auth: true,
  output: 'Session',
  description: 'Read does not count as user activity.',
});
op('/admin/session', 'delete', 'logoutAdmin', '로그아웃', {
  auth: true,
  status: 204,
  description:
    'Invalidate this session and release its leases. Requires CSRF. Expired session returns 401 and client clears local auth state.',
});
op('/admin/session/activity', 'post', 'recordAdminActivity', '실제 사용자 활동 보고', {
  auth: true,
  output: 'Session',
  request: obj({ event: en('INPUT', 'CLICK', 'NAVIGATION') }),
  description:
    'Client coalesces actual activity, proposed at most once per 60 seconds. Use server receipt time. Never called by polling, heartbeat, autosave or hidden timer alone. Cannot revive expired session.',
});
op('/admin/countries', 'get', 'listAdminCountries', '국가 기준 정보', {
  auth: true,
  output: 'CountryList',
  description: 'Seed-managed dictionary; all configured countries, not only visible destinations.',
});
op('/admin/tags', 'get', 'listAdminTags', '관리자 태그 사전', {
  auth: true,
  output: 'TagList',
  description:
    'Includes inactive values for old drafts. New selection of inactive tags rejected. Dictionary CRUD deferred.',
});
op('/admin/cities', 'get', 'listAdminCities', '관리자 도시 목록', {
  auth: true,
  output: 'AdminCityPage',
  parameters: [
    ...page,
    q('q', text(100), 'Optional city/country search'),
    q('visibility', visibility, 'Optional visibility'),
  ],
  description: 'Sort id DESC. Cursor filter-bound. No draft body or lease token returned.',
});
op('/admin/cities', 'post', 'createCity', '도시와 최초 초안 생성', {
  auth: true,
  status: 201,
  request: 'CityCreate',
  output: 'CityCreated',
  location: true,
  errors: [422],
  description:
    'Atomically create HIDDEN city and initial draft; no automatic edit lease. Returns Location for /admin/cities/{id}. Client then acquires a lease. Creates no public content.',
});
op('/admin/cities/{cityId}', 'get', 'getAdminCity', '도시 관리 상태', {
  auth: true,
  output: 'CityAdmin',
  etag: true,
  errors: [404],
  description:
    'ETag covers visibility, country, publication pointers and current draft identity/editVersion. Excludes heartbeat so the validator is stable while simply connected.',
});
op('/admin/cities/{cityId}', 'patch', 'changeCityVisibility', '도시 비공개·준비 중 전환', {
  auth: true,
  lock: true,
  conditional: true,
  request: obj({ visibility: en('HIDDEN', 'PREPARING') }),
  output: 'CityAdmin',
  etag: true,
  errors: [404, 422],
  description:
    'PUBLIC not accepted here. Use draft publication to become public, including republishing an unchanged copy. HIDDEN keeps data; PREPARING requires displayable basics. Audit atomic.',
});
op('/admin/cities/{cityId}', 'delete', 'deleteCity', '도시 물리 삭제와 이력 보존', {
  auth: true,
  lock: true,
  conditional: true,
  status: 204,
  errors: [404],
  description:
    'If-Match from admin city GET; acquire CITY row lock and recompute validator before full history archive + physical delete in same transaction. Does not require valid publishable content. Image cleanup after DB commit. Retained audit remains addressable outside /cities.',
});
op('/admin/cities/{cityId}/edit-lock', 'get', 'getEditLock', '편집 잠금 상태', {
  auth: true,
  output: 'LockView',
  errors: [404],
  description:
    'No secret tokens; expired lease presented unlocked. Does not renew lease or session activity.',
});
op('/admin/cities/{cityId}/edit-lock', 'post', 'acquireEditLock', '편집권 획득', {
  auth: true,
  status: 201,
  request: obj({ editorInstanceId: uuid }),
  output: 'Lease',
  location: true,
  errors: [404, 409],
  description:
    'Atomic acquisition if absent/expired. Active lease including same administrator other tab returns 409. No forced takeover. Bind to authenticated session as well as actor/window. Return raw token once; store only hash.',
});
op('/admin/cities/{cityId}/edit-lock/heartbeats', 'post', 'renewEditLock', '편집 연결 확인', {
  auth: true,
  lock: true,
  output: 'Heartbeat',
  errors: [404],
  description:
    'Every 60 seconds. Valid unexpired lease only, extend to server time +300 seconds. Not user activity. Expired token cannot revive lease.',
});
op('/admin/cities/{cityId}/edit-lock', 'delete', 'releaseEditLock', '편집권 반납', {
  auth: true,
  lock: true,
  status: 204,
  errors: [404],
  description:
    'Client saves successfully before release. Already absent/expired lease with no replacement may return 204; never delete another active lease. Logout handles server-wide session lease release.',
});
op('/admin/cities/{cityId}/draft', 'post', 'createDraft', '현재 공개본에서 수정 초안 생성', {
  auth: true,
  lock: true,
  status: 201,
  output: 'Draft',
  etag: true,
  location: true,
  errors: [404, 409],
  description:
    '409 DRAFT_ALREADY_EXISTS if existing draft. Copy published general content and record current safety basis; do not reset evidence verification dates. Existing draft is opened with GET instead.',
});
op('/admin/cities/{cityId}/draft', 'get', 'getDraft', '편집 초안 조회', {
  auth: true,
  lock: true,
  output: 'Draft',
  etag: true,
  errors: [404],
  description:
    'Requires own valid lease; other admin cannot enter draft editing. Does not renew inactivity timer. Current independent safety is read separately.',
});
op('/admin/cities/{cityId}/draft', 'put', 'replaceDraft', '도시 초안 전체 저장', {
  auth: true,
  lock: true,
  conditional: true,
  request: 'DraftWrite',
  output: 'Draft',
  etag: true,
  errors: [404, 413, 422],
  description:
    'Replace only this mutable draft aggregate. All arrays required; missing is error, [] means empty. IDs must belong to current draft. Safety null means untouched only until first edit; once safety draft exists null is rejected. Server increments editVersion; changing source text invalidates old check unless explicitly reconfirmed. Does not publish.',
});
op('/admin/cities/{cityId}/draft', 'delete', 'discardDraft', '수정 초안 폐기', {
  auth: true,
  lock: true,
  conditional: true,
  status: 204,
  errors: [404, 409],
  description:
    'Delete only draft and draft-owned data; preserve published data/assets. Audit DISCARD_DRAFT when city previously published. Lease remains until release. PREPARING city without published general revision rejects discard until changed to HIDDEN.',
});
op('/admin/cities/{cityId}/draft/preview', 'post', 'previewDraft', '게시 미리보기·검증', {
  auth: true,
  lock: true,
  request: 'PublishRequest',
  output: 'Preview',
  errors: [404, 409],
  description:
    'Read-only evaluation, no save/publication. 200 canPublish:false plus violations for incomplete content or safety conflict. Mismatched expected draft identity/version returns 409. Final publish rechecks everything.',
});
op('/admin/cities/{cityId}/draft/publications', 'post', 'publishDraft', '도시 수정본 전체 게시', {
  auth: true,
  lock: true,
  status: 201,
  request: 'PublishRequest',
  output: 'CityAdmin',
  location: true,
  errors: [404, 409, 422],
  description:
    'Expected draft identity/version required. Recheck preview conditions, evidence, safety base and lease in transaction; swap pointers + history + release lease. Safety untouched retains latest published safety. Edited stale safety rejects all. Location points to historical revision resource. No duplicate auto retry.',
});
op(
  '/admin/cities/{cityId}/draft/safety-reviews',
  'post',
  'reviewDraftSafety',
  '안전 충돌 재검토 완료',
  {
    auth: true,
    lock: true,
    request: 'SafetyReviewRequest',
    output: 'Draft',
    etag: true,
    errors: [404, 409, 422],
    description:
      'UI explicitly compares newest public safety and local draft, then submits reviewed content. Verify reviewedPublicSafetyRevisionId still current, write safety draft and new base, increment draft editVersion atomically. Does not publish or allow discard-and-publish bypass.',
  },
);
op('/admin/cities/{cityId}/safety', 'get', 'getCurrentSafety', '현재 공개 안전정보', {
  auth: true,
  output: 'SafetyView',
  etag: true,
  errors: [404],
  description:
    'Independent from general edit lease. No version => revisionId:null, UNVERIFIED, items:[], ETag for empty safety resource. No mutable general safety draft exposed.',
});
op('/admin/cities/{cityId}/safety', 'put', 'publishSafety', '안전정보 독립 게시', {
  auth: true,
  conditional: true,
  request: 'SafetyWrite',
  output: 'SafetyView',
  etag: true,
  errors: [404, 422],
  description:
    'Replaces current safety representation with a new immutable version after review. If-Match requires newest safety GET. Valid session+CSRF always; NO general edit lease requirement. On stale ETag 412, preserve client input and review again. Does not change visibility or general draft.',
});
op('/admin/cities/{cityId}/photo-assets', 'get', 'listPhotoAssets', '도시 사진 자산 목록', {
  auth: true,
  lock: true,
  output: 'PhotoAssetList',
  errors: [404],
  description:
    'Includes retained assets for reuse in same city. Listing does not attach asset to draft.',
});
const upload = op(
  '/admin/cities/{cityId}/photo-assets',
  'post',
  'uploadPhoto',
  '사진 업로드·권리 메타데이터 등록',
  {
    auth: true,
    lock: true,
    status: 201,
    output: 'PhotoAsset',
    location: true,
    errors: [404, 413, 415, 422],
    description:
      'Proposed limits: JPEG/PNG/WebP only, 10 MiB/file, max 40 megapixels decoded; reject SVG and external URL fetching. Validate bytes, generate storage key, strip unnecessary EXIF. New asset only, never overwrite published files. rightsConfirmed stamps server time. Recheck lease after upload. Attach via draft PUT.',
  },
);
upload.requestBody = {
  required: true,
  content: {
    'multipart/form-data': {
      schema: obj({
        file: { type: 'string', format: 'binary' },
        metadata: ref('PhotoMetadataWrite'),
      }),
      encoding: { metadata: { contentType: 'application/json' } },
    },
  },
};
op('/admin/source-documents', 'get', 'listSourceDocuments', '등록한 출처 검색', {
  auth: true,
  output: 'SourcePage',
  parameters: [...page, q('q', text(100), 'Title/URL/publisher search')],
  description: 'Shared immutable source dictionary. No remote page fetching.',
});
op('/admin/source-documents', 'post', 'createSourceDocument', '출처 문서 등록', {
  auth: true,
  status: 201,
  request: 'SourceDocumentWrite',
  output: 'SourceDocument',
  location: true,
  errors: [422],
  description:
    'Registers URL/title/publisher only; no fetching and no verification timestamp. Checking a content claim occurs in draft source links.',
});
op('/admin/source-documents/{sourceId}', 'get', 'getSourceDocument', '출처 문서 상세', {
  auth: true,
  output: 'SourceDocument',
  errors: [404],
});
op(
  '/admin/cities/{cityId}/draft/experience-suggestions',
  'post',
  'suggestExperiences',
  '입력 자료로 AI 경험 후보 생성',
  {
    auth: true,
    lock: true,
    request: 'AiSuggestionRequest',
    output: 'AiSuggestionResponse',
    errors: [404, 409, 413, 422, 429, 502, 504],
    description:
      'Proposal: synchronous request capped at 30 seconds, no DB transaction held during AI call. Input 1-5 registered sources, <=30000 characters total. IDs must be registered, output citations limited to input IDs. Return <=5 unverified suggestions; never save/publish automatically. Apply selected suggestions with current draft PUT/If-Match. AI provider not chosen yet.',
  },
);
op(
  '/admin/cities/{cityId}/revisions/{revisionId}',
  'get',
  'getHistoricalPublication',
  '과거 일반 공개본 조회',
  {
    auth: true,
    output: 'HistoricalPublication',
    errors: [404],
    description:
      'Published historical versions only. Includes safety as of that publication, not latest safety. City deleted => 404; deletion history remains in audit APIs. No restore API.',
  },
);
op('/admin/audit-events', 'get', 'listAuditEvents', '삭제된 도시를 포함한 이력 목록', {
  auth: true,
  output: 'AuditPage',
  parameters: [...page, q('cityRefId', ref('Id'), 'Historical city ID; need not exist')],
  description:
    'Sort occurredAt DESC,id DESC; opaque cursor encodes pair and filter. Deleted city histories remain searchable.',
});
op('/admin/audit-events/{eventId}', 'get', 'getAuditEvent', '이력 스냅샷 상세', {
  auth: true,
  output: 'AuditDetail',
  errors: [404],
  description:
    'Versioned before/after snapshots. Sensitive auth fields excluded. Image bytes not archived.',
});

const doc = {
  openapi: '3.1.1',
  info: {
    title: 'TripeE MVP API',
    version: '0.1.0',
    description:
      'Baseline approved 2026-10-01; not an implemented server. Companion: docs/api-v1.md. JSON camelCase; bigint IDs are strings. Admin session + CSRF. Workflow constraints remain normative in companion document.',
  },
  servers: [
    {
      url: '/api/v1',
      description: 'Same-origin frontend/API proxy; local port is deployment configuration',
    },
  ],
  tags: [{ name: 'Public' }, { name: 'Admin' }],
  paths,
  components: {
    securitySchemes: {
      AdminSession: {
        type: 'apiKey',
        in: 'cookie',
        name: 'TRIPEE_SESSION',
        description:
          'Server-side session. HttpOnly, SameSite=Lax; Secure on HTTPS. Cookie name is proposed configuration, not framework default.',
      },
    },
    schemas,
  },
};
await writeFile(
  new URL('../../docs/openapi-v1.json', import.meta.url),
  JSON.stringify(doc, null, 2) + '\n',
);
const operations = Object.values(paths).reduce((n, v) => n + Object.keys(v).length, 0);
console.log(
  `Generated ${Object.keys(paths).length} paths, ${operations} operations, ${Object.keys(schemas).length} schemas.`,
);

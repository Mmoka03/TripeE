// 관리자 작업 흐름을 확인하는 로컬 시연 서비스임. 실제 인증·API·DB 처리는 포함하지 않음.
// 실제 서버 데이터와 섞이지 않도록 별도 localStorage 키를 사용함.
export const DEMO_KEY = 'tripee.admin-demo.v1';
const clone = (value) => structuredClone(value);
const id = () => crypto.randomUUID();
export class WorkflowError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}
const fail = (code, message) => {
  throw new WorkflowError(code, message);
};
const kinds = ['EXPERIENCE', 'VISIT_PERIOD', 'ARRIVAL', 'LOCAL_TRANSPORT'];
export const kindNames = {
  EXPERIENCE: '대표 경험',
  VISIT_PERIOD: '방문 시기',
  ARRIVAL: '도시 접근',
  LOCAL_TRANSPORT: '현지 이동',
  CONSIDERATION: '고려사항',
};
export function blankSafety() {
  return {
    reviewState: 'UNVERIFIED',
    title: '',
    body: '',
    kind: 'LOCAL_CAUTION',
    assessment: 'CAUTION',
    officialLevel: '',
    scope: 'CITY',
    coverage: 'WHOLE_CITY',
    scopeNote: '',
    sourceUrl: '',
    evidence: '',
    confirmed: false,
  };
}
export function blankDraft(city) {
  return {
    id: id(),
    editVersion: 0,
    nameKo: city.nameKo || '',
    nameEn: city.nameEn || '',
    latitude: '',
    longitude: '',
    tagline: '',
    tags: [],
    photos: [],
    contentItems: [],
    baseSafetyId: city.safety?.id || null,
    safetyDraft: null,
    updatedBy: null,
    savedAt: null,
  };
}
export function seedDemo() {
  const definitions = [
    ['1', '교토', 'Kyoto', '일본', 'JP', 35.0116, 135.7681, 'PUBLIC'],
    ['2', '나폴리', 'Naples', '이탈리아', 'IT', 40.8518, 14.2681, 'PREPARING'],
    ['3', '베이징', 'Beijing', '중국', 'CN', 39.9042, 116.4074, 'HIDDEN'],
  ];
  const cities = definitions.map(
    ([cityId, nameKo, nameEn, country, countryCode, latitude, longitude, visibility]) => {
      const city = {
        id: cityId,
        nameKo,
        nameEn,
        country,
        countryCode,
        visibility,
        safety: {
          id: id(),
          ...blankSafety(),
          publishedAt: new Date().toISOString(),
          publishedBy: 'A',
        },
        published: null,
        draft: null,
        lock: null,
      };
      const draft = {
        ...blankDraft(city),
        nameKo,
        nameEn,
        latitude,
        longitude,
        tagline: '검토 흐름을 확인하기 위한 시연용 도시 소개입니다.',
        tags: ['문화', '도보 여행'],
        photos: [
          {
            id: id(),
            name: '시연용 사진 자리',
            alt: '시연용 도시 사진 자리',
            sourceUrl: 'https://example.com/demo-photo',
            creator: '시연 데이터',
            license: '시연 전용 / 실제 이용 권리 미확인',
            rights: true,
            previewUrl: null,
          },
        ],
        contentItems: kinds.map((kind, index) => ({
          id: id(),
          kind,
          title: `${kindNames[kind]} 시연 항목`,
          body: '화면과 게시 검증을 확인하는 샘플입니다. 실제 여행 정보가 아닙니다.',
          selected: true,
          sourceUrl: 'https://example.com/demo-source',
          evidence: '시연 데이터의 검토 상태만 시험합니다.',
          confirmed: true,
          order: index + 1,
        })),
        savedAt: new Date().toISOString(),
        updatedBy: 'A',
      };
      if (visibility === 'PUBLIC') city.published = clone(draft);
      city.draft = clone(draft);
      if (cityId === '1') city.draft.photos[0].alt = '';
      if (cityId === '2')
        city.lock = { editor: 'seed-b', owner: 'B', token: id(), expiresAt: Date.now() + 300000 };
      return city;
    },
  );
  return {
    schema: 1,
    cities,
    events: [
      {
        id: id(),
        cityId: '1',
        cityName: '교토',
        action: 'PUBLISH',
        actor: 'A',
        at: new Date().toISOString(),
        before: null,
        after: clone(cities[0]),
      },
    ],
  };
}
export function safetyProblems(safety) {
  if (!safety || safety.reviewState === 'UNVERIFIED') return [];
  const errors = [];
  for (const [key, label] of [
    ['title', '안전 제목'],
    ['body', '안전 본문'],
    ['scopeNote', '도시 적용 근거'],
    ['evidence', '안전 출처 근거'],
  ])
    if (!safety[key]?.trim()) errors.push(label + '을 입력해주세요.');
  if (!/^https?:\/\/\S+$/i.test(safety.sourceUrl)) errors.push('안전 출처 URL을 확인해주세요.');
  if (!safety.confirmed) errors.push('안전정보 근거를 확인해주세요.');
  if (safety.body.length > 3000 || safety.evidence.length > 4000 || safety.scopeNote.length > 4000)
    errors.push('안전정보의 글자 수 제한을 확인해주세요.');
  if (safety.reviewState === 'CHECKED' && safety.kind !== 'OFFICIAL_ADVISORY')
    errors.push('확인 완료에는 공식 경보 또는 공식 미발령 확인 항목이 필요합니다.');
  if (
    safety.kind === 'OFFICIAL_ADVISORY' &&
    safety.assessment === 'ISSUED' &&
    !safety.officialLevel
  )
    errors.push('공식 발령 단계를 선택해주세요.');
  return errors;
}
export function publicationProblems(draft) {
  if (!draft) return [{ tab: 'basic', message: '수정 초안을 만들어주세요.' }];
  const errors = [],
    add = (tab, message) => errors.push({ tab, message });
  for (const [key, label, max] of [
    ['nameKo', '한국어 도시명', 150],
    ['nameEn', '영문 도시명', 150],
    ['tagline', '한 줄 소개', 200],
  ])
    if (!draft[key]?.trim() || draft[key].length > max) add('basic', label + '을 확인해주세요.');
  if (
    draft.latitude === '' ||
    !Number.isFinite(Number(draft.latitude)) ||
    Math.abs(Number(draft.latitude)) > 90 ||
    draft.longitude === '' ||
    !Number.isFinite(Number(draft.longitude)) ||
    Math.abs(Number(draft.longitude)) > 180
  )
    add('basic', '위도·경도를 확인해주세요.');
  if (!draft.photos.length) add('photos', '사진을 최소 한 장 선택해주세요.');
  draft.photos.forEach((p, i) => {
    if (!p.alt.trim()) add('photos', `사진 ${i + 1}의 대체 텍스트가 필요합니다.`);
    if (!p.rights || !p.license.trim() || !/^https?:\/\/\S+$/i.test(p.sourceUrl))
      add('photos', `사진 ${i + 1}의 출처·이용 조건 확인이 필요합니다.`);
  });
  kinds.forEach((kind) => {
    if (!draft.contentItems.some((c) => c.kind === kind && c.selected))
      add('content', `${kindNames[kind]}을 최소 한 개 선정해주세요.`);
  });
  draft.contentItems
    .filter((c) => c.selected)
    .forEach((c, i) => {
      if (
        !c.title.trim() ||
        !c.body.trim() ||
        c.title.length > 200 ||
        c.body.length > 3000 ||
        !/^https?:\/\/\S+$/i.test(c.sourceUrl) ||
        !c.evidence.trim() ||
        c.evidence.length > 4000 ||
        !c.confirmed
      )
        add('content', `선정 항목 ${i + 1}의 제목·본문·출처·검토 상태를 확인해주세요.`);
    });
  safetyProblems(draft.safetyDraft).forEach((message) => add('safety', message));
  return errors;
}

export function createDemoService(storage, now = () => Date.now()) {
  const read = () => {
    const raw = storage.getItem(DEMO_KEY);
    if (!raw) {
      const seed = seedDemo();
      storage.setItem(DEMO_KEY, JSON.stringify(seed));
      return seed;
    }
    const state = JSON.parse(raw);
    if (state.schema !== 1)
      fail('DEMO_FORMAT', '시연 데이터 형식을 읽을 수 없어요. 시연 초기화를 사용해주세요.');
    return state;
  };
  const write = (state) => {
    storage.setItem(DEMO_KEY, JSON.stringify(state));
    return clone(state);
  };
  const city = (state, cityId) =>
    state.cities.find((c) => c.id === cityId) ||
    fail('CITY_NOT_FOUND', '도시가 삭제됐거나 존재하지 않아요.');
  const authorize = (ctx) => {
    if (!ctx?.user || !ctx?.editor) fail('AUTH_REQUIRED', '시연 관리자 선택이 필요해요.');
    if (now() - ctx.lastActivity >= 3600000)
      fail('SESSION_EXPIRED', '1시간 동안 활동이 없어 세션이 만료됐어요.');
  };
  const lease = (c, ctx) => {
    authorize(ctx);
    if (!c.lock || c.lock.expiresAt <= now())
      fail('EDIT_LOCK_EXPIRED', '편집권이 만료됐어요. 입력을 복사하고 다시 확인해주세요.');
    if (c.lock.editor !== ctx.editor || c.lock.token !== ctx.token)
      fail('EDIT_LOCK_HELD', `${c.lock.owner} 관리자가 편집 중이에요.`);
  };
  // 삭제 후에도 콘텐츠 이력을 남기되 편집권 토큰은 감사 데이터에서 제외함.
  const snapshot = (value) =>
    JSON.parse(
      JSON.stringify(value, (key, val) =>
        key === 'lock' || key === 'token' || key === 'editor' ? undefined : val,
      ),
    );
  const event = (s, c, ctx, action, before, after) =>
    s.events.unshift({
      id: id(),
      cityId: c.id,
      cityName: c.nameKo || c.draft?.nameKo || '새 도시',
      actor: ctx.user,
      action,
      at: new Date(now()).toISOString(),
      before: snapshot(before),
      after: snapshot(after),
    });
  return {
    read,
    acquire(cityId, ctx) {
      authorize(ctx);
      const s = read(),
        c = city(s, cityId);
      if (c.lock && c.lock.expiresAt > now() && c.lock.editor !== ctx.editor)
        fail(
          'EDIT_LOCK_HELD',
          `${c.lock.owner} 관리자가 편집 중이에요. 안전정보는 별도로 수정할 수 있어요.`,
        );
      c.lock = { owner: ctx.user, editor: ctx.editor, token: id(), expiresAt: now() + 300000 };
      if (!c.draft)
        c.draft = {
          ...clone(c.published || blankDraft(c)),
          id: id(),
          editVersion: 0,
          baseSafetyId: c.safety?.id || null,
          safetyDraft: null,
        };
      write(s);
      return clone(c);
    },
    // 본인 잠금만 명시적으로 재개함. 토큰 교체 후 이전 화면의 쓰기를 차단함.
    reclaim(cityId, expectedToken, ctx) {
      authorize(ctx);
      const s = read(),
        c = city(s, cityId);
      if (!c.lock || c.lock.expiresAt <= now() || c.lock.token !== expectedToken)
        fail('EDIT_LOCK_CHANGED', '편집권 상태가 바뀌었어요. 목록에서 다시 확인해주세요.');
      if (c.lock.owner !== ctx.user)
        fail('EDIT_LOCK_HELD', '다른 관리자의 편집권은 가져올 수 없어요.');
      c.lock = { owner: ctx.user, editor: ctx.editor, token: id(), expiresAt: now() + 300000 };
      write(s);
      return clone(c);
    },
    heartbeat(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      c.lock.expiresAt = now() + 300000;
      write(s);
      return c.lock.expiresAt;
    },
    release(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      if (c.lock?.editor === ctx.editor && c.lock?.token === ctx.token) {
        c.lock = null;
        write(s);
      }
    },
    // 초안 버전 확인 후 저장함. 수정한 안전정보를 빼서 충돌을 우회할 수 없도록 함.
    save(cityId, draft, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      if (c.draft.id !== draft.id || c.draft.editVersion !== draft.editVersion)
        fail(
          'DRAFT_VERSION_MISMATCH',
          '다른 변경이 저장됐어요. 입력을 복사하고 최신 초안을 확인해주세요.',
        );
      if (c.draft.safetyDraft && !draft.safetyDraft)
        fail(
          'SAFETY_DRAFT_CANNOT_BE_DROPPED',
          '수정한 안전정보만 제거할 수 없어요. 최신 정보와 재검토해주세요.',
        );
      const before = clone(c.draft);
      c.draft = {
        ...clone(draft),
        editVersion: draft.editVersion + 1,
        updatedBy: ctx.user,
        savedAt: new Date(now()).toISOString(),
      };
      event(s, c, ctx, 'UPDATE_DRAFT', before, c.draft);
      write(s);
      return clone(c.draft);
    },
    // 모든 게시 조건을 먼저 검사함. 실제 구현에서는 검사와 반영을 서버 트랜잭션으로 묶어야 함.
    publish(cityId, expected, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      if (!c.draft || c.draft.id !== expected.id || c.draft.editVersion !== expected.editVersion)
        fail('DRAFT_VERSION_MISMATCH', '미리보기 이후 초안이 바뀌었어요. 다시 확인해주세요.');
      if (publicationProblems(c.draft).length)
        fail('PUBLICATION_NOT_READY', '게시 필수 항목을 확인해주세요.');
      if (c.draft.safetyDraft && c.draft.baseSafetyId !== c.safety?.id)
        fail('SAFETY_CHANGED', '최신 안전정보와 수정 내용을 다시 검토해야 해요.');
      const before = clone(c);
      if (c.draft.safetyDraft)
        c.safety = {
          ...clone(c.draft.safetyDraft),
          id: id(),
          publishedAt: new Date(now()).toISOString(),
          publishedBy: ctx.user,
        };
      c.published = { ...clone(c.draft), safetyAtPublish: clone(c.safety) };
      c.nameKo = c.draft.nameKo;
      c.nameEn = c.draft.nameEn;
      c.visibility = 'PUBLIC';
      c.draft = null;
      c.lock = null;
      event(s, c, ctx, 'PUBLISH', before, c);
      return write(s);
    },
    // 일반 편집권 없이 게시하되 안전정보 기반 버전은 일치해야 함.
    publishSafety(cityId, safety, expectedId, ctx) {
      authorize(ctx);
      const s = read(),
        c = city(s, cityId);
      if (c.safety?.id !== expectedId)
        fail('SAFETY_CHANGED', '안전정보가 갱신됐어요. 입력을 보존하고 최신본을 재검토해주세요.');
      if (safetyProblems(safety).length)
        fail('VALIDATION_FAILED', safetyProblems(safety).join(' '));
      const before = clone(c.safety);
      c.safety = {
        ...clone(safety),
        id: id(),
        publishedAt: new Date(now()).toISOString(),
        publishedBy: ctx.user,
      };
      event(s, c, ctx, 'SAFETY_PUBLISH', before, c.safety);
      return write(s);
    },
    // 최신 안전정보 재검토 결과를 초안에만 반영함. 공개는 별도 게시에서 처리함.
    review(cityId, draft, latestId, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      if (c.safety?.id !== latestId)
        fail('SAFETY_CHANGED', '재검토 중 안전정보가 다시 바뀌었어요.');
      if (draft.editVersion !== c.draft.editVersion || draft.id !== c.draft.id)
        fail('DRAFT_VERSION_MISMATCH', '최신 초안을 다시 확인해주세요.');
      const before = clone(c.draft);
      c.draft = {
        ...clone(draft),
        baseSafetyId: latestId,
        editVersion: draft.editVersion + 1,
        savedAt: new Date(now()).toISOString(),
        updatedBy: ctx.user,
      };
      event(s, c, ctx, 'UPDATE_DRAFT', before, c.draft);
      write(s);
      return clone(c.draft);
    },
    discard(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      if (c.visibility === 'PREPARING' && !c.published)
        fail('PREPARING_REQUIRES_REVISION', '준비 중 도시를 먼저 비공개로 전환해주세요.');
      event(s, c, ctx, 'DISCARD_DRAFT', c.draft, null);
      c.draft = null;
      c.lock = null;
      return write(s);
    },
    visibility(cityId, visibility, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      if (!['HIDDEN', 'PREPARING'].includes(visibility))
        fail('VALIDATION_FAILED', '재공개는 초안 게시로 진행해주세요.');
      if (
        visibility === 'PREPARING' &&
        !(
          c.draft?.nameKo &&
          c.draft?.nameEn &&
          c.draft?.latitude !== '' &&
          c.draft?.longitude !== ''
        )
      )
        fail('VALIDATION_FAILED', '준비 중 표시에 필요한 기본 정보를 저장해주세요.');
      const before = c.visibility;
      c.visibility = visibility;
      event(s, c, ctx, 'SET_VISIBILITY', { visibility: before }, { visibility });
      return write(s);
    },
    remove(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      const snapshot = clone(c);
      event(s, c, ctx, 'DELETE', snapshot, null);
      s.cities = s.cities.filter((x) => x.id !== cityId);
      return write(s);
    },
    create(countryCode, ctx) {
      authorize(ctx);
      const s = read();
      const defs = { JP: '일본', IT: '이탈리아', CN: '중국' };
      if (!defs[countryCode]) fail('VALIDATION_FAILED', '국가를 선택해주세요.');
      const c = {
        id: id(),
        nameKo: '',
        nameEn: '',
        country: defs[countryCode],
        countryCode,
        visibility: 'HIDDEN',
        safety: { id: id(), ...blankSafety() },
        published: null,
        draft: null,
        lock: null,
      };
      c.draft = blankDraft(c);
      s.cities.unshift(c);
      event(s, c, ctx, 'CREATE_DRAFT', null, c);
      write(s);
      return c.id;
    },
    expire(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      lease(c, ctx);
      c.lock.expiresAt = now() - 1;
      return write(s);
    },
    simulateSafetyChange(cityId, ctx) {
      const s = read(),
        c = city(s, cityId);
      authorize(ctx);
      const before = clone(c.safety);
      c.safety = {
        ...clone(c.safety),
        id: id(),
        publishedAt: new Date(now()).toISOString(),
        publishedBy: ctx.user === 'A' ? 'B' : 'A',
      };
      event(s, c, { ...ctx, user: c.safety.publishedBy }, 'SAFETY_PUBLISH', before, c.safety);
      return write(s);
    },
    reset() {
      return write(seedDemo());
    },
  };
}

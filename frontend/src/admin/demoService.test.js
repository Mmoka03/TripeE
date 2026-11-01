import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoService, publicationProblems, blankSafety } from './demoService.js';

function setup() {
  const data = new Map();
  let clock = Date.now();
  const storage = { getItem: (k) => data.get(k) || null, setItem: (k, v) => data.set(k, v) };
  const service = createDemoService(storage, () => clock);
  const a = { user: 'A', editor: 'tab-a', lastActivity: clock },
    b = { user: 'B', editor: 'tab-b', lastActivity: clock };
  const acquired = service.acquire('1', a);
  a.token = acquired.lock.token;
  const draft = acquired.draft;
  draft.photos[0].alt = '샘플 사진';
  return {
    service,
    a,
    b,
    draft,
    advance: (ms) => {
      clock += ms;
    },
  };
}
const error = (code) => (e) => e.code === code;

test('초안 저장은 공개본을 바꾸지 않는다', () => {
  const { service, a, draft } = setup();
  const previous = service.read().cities[0].published.tagline;
  draft.tagline = '새 소개';
  const saved = service.save('1', draft, a);
  assert.equal(saved.editVersion, 1);
  assert.equal(service.read().cities[0].published.tagline, previous);
});
test('같은 계정의 다른 탭도 편집권을 공유하지 않는다', () => {
  const { service, a } = setup();
  assert.throws(() => service.acquire('1', { ...a, editor: 'other-tab' }), error('EDIT_LOCK_HELD'));
});

test('본인 잠금 재개는 토큰을 교체하고 이전 화면의 저장과 heartbeat를 거부한다', () => {
  const { service, a, draft } = setup();
  const next = { ...a, editor: 'reopened-tab', token: null };
  const resumed = service.reclaim('1', a.token, next);
  next.token = resumed.lock.token;
  assert.notEqual(next.token, a.token);
  assert.equal(resumed.draft.id, draft.id);
  assert.throws(() => service.save('1', draft, a), error('EDIT_LOCK_HELD'));
  assert.throws(() => service.heartbeat('1', a), error('EDIT_LOCK_HELD'));
  service.release('1', a);
  assert.equal(service.read().cities[0].lock.token, next.token);
  assert.ok(service.save('1', resumed.draft, next));
});

test('다른 관리자는 잠금을 재개할 수 없다', () => {
  const { service, a, b } = setup();
  assert.throws(() => service.reclaim('1', a.token, b), error('EDIT_LOCK_HELD'));
  assert.equal(service.read().cities[0].lock.token, a.token);
});

test('재개 확인 중 바뀌거나 만료된 잠금은 덮어쓰지 않는다', () => {
  const { service, a, advance } = setup();
  const next = { ...a, editor: 'reopened-tab' };
  const resumed = service.reclaim('1', a.token, next);
  assert.throws(() => service.reclaim('1', a.token, a), error('EDIT_LOCK_CHANGED'));
  advance(300000);
  assert.throws(() => service.reclaim('1', resumed.lock.token, next), error('EDIT_LOCK_CHANGED'));
});

test('재개는 비활동 세션 만료를 우회하지 않는다', () => {
  const { service, a, advance } = setup();
  advance(3600000);
  assert.throws(
    () => service.reclaim('1', a.token, { ...a, editor: 'reopened-tab' }),
    error('SESSION_EXPIRED'),
  );
});
test('만료된 편집권은 heartbeat로 되살릴 수 없다', () => {
  const { service, a, advance } = setup();
  advance(300000);
  assert.throws(() => service.heartbeat('1', a), error('EDIT_LOCK_EXPIRED'));
});
test('만료 후 새 소유자가 얻으면 이전 저장을 거부한다', () => {
  const { service, a, b, draft, advance } = setup();
  advance(300001);
  service.acquire('1', b);
  assert.throws(() => service.save('1', draft, a), error('EDIT_LOCK_HELD'));
});
test('오래된 editVersion 저장은 거부한다', () => {
  const { service, a, draft } = setup();
  service.save('1', draft, a);
  assert.throws(() => service.save('1', draft, a), error('DRAFT_VERSION_MISMATCH'));
});
test('독립 안전 게시에는 일반 편집권이 필요하지 않다', () => {
  const { service, b } = setup();
  const c = service.read().cities[0];
  service.publishSafety('1', blankSafety(), c.safety.id, b);
  const after = service.read().cities[0];
  assert.equal(after.lock.owner, 'A');
  assert.equal(after.draft.id, c.draft.id);
  assert.equal(after.visibility, c.visibility);
});
test('안전을 수정하지 않은 일반 게시는 최신 안전정보를 유지한다', () => {
  const { service, a, b, draft } = setup();
  const saved = service.save('1', draft, a);
  const c = service.read().cities[0];
  service.publishSafety('1', blankSafety(), c.safety.id, b);
  const latest = service.read().cities[0].safety.id;
  service.publish('1', saved, a);
  const after = service.read().cities[0];
  assert.equal(after.safety.id, latest);
  assert.equal(after.draft, null);
  assert.equal(after.lock, null);
});
test('안전 수정 중 최신이 바뀌면 일반 게시 전체가 롤백된다', () => {
  const { service, a, b, draft } = setup();
  draft.safetyDraft = blankSafety();
  draft.tagline = '게시되면 안 되는 소개';
  const saved = service.save('1', draft, a),
    before = service.read().cities[0];
  service.publishSafety('1', blankSafety(), before.safety.id, b);
  assert.throws(() => service.publish('1', saved, a), error('SAFETY_CHANGED'));
  assert.equal(service.read().cities[0].published.tagline, before.published.tagline);
  assert.equal(service.read().cities[0].draft.tagline, draft.tagline);
});
test('재검토 저장만으로 공개되지 않으며 재게시가 가능하다', () => {
  const { service, a, b, draft } = setup();
  draft.safetyDraft = blankSafety();
  const saved = service.save('1', draft, a);
  service.publishSafety('1', blankSafety(), service.read().cities[0].safety.id, b);
  const latest = service.read().cities[0].safety.id,
    reviewed = service.review('1', saved, latest, a);
  assert.ok(service.read().cities[0].draft);
  service.publish('1', reviewed, a);
  assert.equal(service.read().cities[0].draft, null);
});
test('독립 안전 게시의 오래된 기반은 거부한다', () => {
  const { service, a, b } = setup();
  const old = service.read().cities[0].safety.id;
  service.publishSafety('1', blankSafety(), old, a);
  assert.throws(() => service.publishSafety('1', blankSafety(), old, b), error('SAFETY_CHANGED'));
});
test('수정한 안전정보만 지워 충돌을 우회할 수 없다', () => {
  const { service, a, draft } = setup();
  draft.safetyDraft = blankSafety();
  const saved = service.save('1', draft, a);
  assert.throws(
    () => service.save('1', { ...saved, safetyDraft: null }, a),
    error('SAFETY_DRAFT_CANNOT_BE_DROPPED'),
  );
});
test('사진 alt 누락을 게시 전에 검출한다', () => {
  const { service } = setup();
  assert.ok(publicationProblems(service.read().cities[0].draft).some((p) => p.tab === 'photos'));
});
test('선정한 네 번째 경험도 근거가 있어야 한다', () => {
  const { draft } = setup();
  draft.contentItems.push({ ...draft.contentItems[0], id: 'fourth', confirmed: false });
  assert.ok(publicationProblems(draft).some((p) => p.tab === 'content'));
});
test('비공개는 데이터를 유지하고 재공개는 게시 경로만 허용한다', () => {
  const { service, a } = setup();
  service.visibility('1', 'HIDDEN', a);
  assert.ok(service.read().cities[0].published);
  assert.throws(() => service.visibility('1', 'PUBLIC', a), error('VALIDATION_FAILED'));
});
test('삭제 후 감사 스냅샷은 유지하고 편집 토큰은 남기지 않는다', () => {
  const { service, a } = setup();
  service.remove('1', a);
  const after = service.read();
  assert.equal(
    after.cities.some((c) => c.id === '1'),
    false,
  );
  const e = after.events.find((x) => x.action === 'DELETE');
  assert.equal(e.before.nameKo, '교토');
  assert.equal(JSON.stringify(after.events).includes(a.token), false);
  assert.equal(e.before.lock, undefined);
});
test('초안 폐기는 공개본을 보존한다', () => {
  const { service, a } = setup();
  service.discard('1', a);
  const c = service.read().cities[0];
  assert.ok(c.published);
  assert.equal(c.draft, null);
  assert.equal(c.lock, null);
});
test('한 시간 비활동 세션의 쓰기를 거부한다', () => {
  const { service, a, advance } = setup();
  advance(3600000);
  assert.throws(
    () => service.publishSafety('1', blankSafety(), 'any', a),
    error('SESSION_EXPIRED'),
  );
});
test('국가를 선택하면 비공개 도시와 초안을 생성한다', () => {
  const { service, a } = setup();
  const id = service.create('IT', a),
    c = service.read().cities.find((x) => x.id === id);
  assert.equal(c.country, '이탈리아');
  assert.equal(c.visibility, 'HIDDEN');
  assert.ok(c.draft);
  assert.equal(c.lock, null);
});

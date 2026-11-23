import test from 'node:test';
import assert from 'node:assert/strict';
import { createAdminApi, AdminApiError } from './adminApi.js';

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
function mock(responses) {
  const calls = [];
  const api = createAdminApi(async (url, options) => {
    calls.push({ url, ...options });
    const response = responses.shift();
    if (response instanceof Error) throw response;
    assert.ok(response, `예상하지 않은 요청: ${url}`);
    return response;
  });
  return { api, calls };
}

test('로그인 전 CSRF를 발급하고 로그인 이후 토큰을 교체해 활동·로그아웃에 사용함', async () => {
  const { api, calls } = mock([
    json({ headerName: 'X-CSRF-TOKEN', token: 'before-login' }),
    json({ admin: { id: '1' } }),
    json({ headerName: 'X-CSRF-TOKEN', token: 'after-login' }),
    json({ admin: { id: '1' } }),
    new Response(null, { status: 204 }),
  ]);
  await api.login('admin-test', 'test-only');
  await api.activity('CLICK');
  await api.logout();
  assert.equal(calls.length, 5);
  assert.equal(calls[1].headers['X-CSRF-TOKEN'], 'before-login');
  assert.deepEqual(JSON.parse(calls[1].body), { loginId: 'admin-test', password: 'test-only' });
  assert.equal(calls[3].headers['X-CSRF-TOKEN'], 'after-login');
  assert.equal(calls[4].headers['X-CSRF-TOKEN'], 'after-login');
  assert.equal(calls[4].method, 'DELETE');
  for (const call of calls) {
    assert.equal(call.credentials, 'same-origin');
    assert.equal(call.cache, 'no-store');
  }
});

test('세션 복원과 도시 조회는 CSRF 발급 없이 읽기 요청만 보냄', async () => {
  const { api, calls } = mock([
    json({ admin: { id: '1' } }),
    json({ items: [], nextCursor: null }),
    json({ id: '1' }),
  ]);
  await api.session();
  await api.cities({ q: ' 교토 & 일본 ', visibility: 'PUBLIC', cursor: 'opaque+/=' });
  await api.city('1');
  const query = new URL(calls[1].url, 'http://localhost').searchParams;
  assert.equal(query.get('q'), '교토 & 일본');
  assert.equal(query.get('visibility'), 'PUBLIC');
  assert.equal(query.get('cursor'), 'opaque+/=');
  assert.equal(query.get('limit'), '20');
  assert.deepEqual(
    calls.map((call) => call.method),
    ['GET', 'GET', 'GET'],
  );
});

test('전체 상태·빈 검색어·첫 페이지는 선택 필터와 커서를 보내지 않음', async () => {
  const { api, calls } = mock([json({ items: [], nextCursor: null })]);
  await api.cities({ q: '  ', visibility: '' });
  assert.equal(calls[0].url, '/api/v1/admin/cities?limit=20');
});

test('CSRF 오류는 변경 요청을 자동 재전송하지 않고 다음 수동 요청 때 갱신함', async () => {
  const { api, calls } = mock([
    json({ headerName: 'X-CSRF-TOKEN', token: 'old' }),
    json({ code: 'CSRF_INVALID' }, 403),
    json({ headerName: 'X-CSRF-TOKEN', token: 'new' }),
    new Response(null, { status: 204 }),
  ]);
  await assert.rejects(
    api.logout(),
    (error) => error.status === 403 && error.code === 'CSRF_INVALID',
  );
  assert.equal(calls.length, 2);
  await api.logout();
  assert.equal(calls[3].headers['X-CSRF-TOKEN'], 'new');
});

test('세션 만료를 구분하고 후속 로그인을 위한 토큰을 새로 받음', async () => {
  const { api, calls } = mock([
    json({ headerName: 'X-CSRF-TOKEN', token: 'old' }),
    json({ code: 'SESSION_EXPIRED' }, 401),
    json({ headerName: 'X-CSRF-TOKEN', token: 'new' }),
    json({ admin: { id: '1' } }),
  ]);
  await assert.rejects(
    api.activity('INPUT'),
    (error) => error.status === 401 && error.code === 'SESSION_EXPIRED',
  );
  await api.login('admin-test', 'test-only');
  assert.equal(calls[3].headers['X-CSRF-TOKEN'], 'new');
});

test('로그인 실패·누락 도시·비정상 응답·연결 실패를 안전한 안내로 변환함', async () => {
  const { api } = mock([
    json({ headerName: 'X-CSRF-TOKEN', token: 'token' }),
    json({ code: 'INVALID_CREDENTIALS', detail: 'private detail' }, 401),
    json({}, 404),
    new Response('<html>vite</html>', { headers: { 'Content-Type': 'text/html' } }),
    new Response('{', { headers: { 'Content-Type': 'application/json' } }),
    new TypeError('connection refused'),
  ]);
  await assert.rejects(
    api.login('bad', 'test-only'),
    (error) => error.message === '아이디 또는 비밀번호를 확인해주세요.',
  );
  await assert.rejects(api.city('missing'), (error) => error.status === 404);
  await assert.rejects(api.session(), (error) => error.code === 'INVALID_RESPONSE');
  await assert.rejects(api.session(), (error) => error.code === 'INVALID_RESPONSE');
  await assert.rejects(
    api.session(),
    (error) => error instanceof AdminApiError && error.status === 503,
  );
});

test('화면 이동으로 취소된 요청을 통신 오류로 바꾸지 않음', async () => {
  const controller = new AbortController();
  controller.abort();
  const { api, calls } = mock([new DOMException('Aborted', 'AbortError')]);
  await assert.rejects(api.cities({ signal: controller.signal }), { name: 'AbortError' });
  assert.equal(calls[0].signal, controller.signal);
});

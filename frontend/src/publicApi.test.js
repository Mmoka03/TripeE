import test from 'node:test';
import assert from 'node:assert/strict';
import { getJson, mapCity, mapSearchResult, safeLink } from './publicApi.js';

const city = {
  id: '9007199254740993',
  nameKo: '교토',
  nameEn: 'Kyoto',
  visibility: 'PUBLIC',
  country: { id: '9', code: 'JP', nameKo: '일본', nameEn: 'Japan' },
  coordinates: { latitude: 35.01161, longitude: 135.76811 },
};
test('DB의 큰 문자열 ID를 유지하고 지도 좌표는 경도·위도 순서로 변환함', () => {
  const mapped = mapCity(city);
  assert.equal(mapped.id, '9007199254740993');
  assert.deepEqual(mapped.coordinates, [135.76811, 35.01161]);
  assert.equal(mapSearchResult({ type: 'CITY', city }, [], []).id, mapped.id);
});
test('국가 검색은 국가 코드에 해당하는 범위로 이동하며 미등록 경계는 도시 좌표를 사용함', () => {
  const result = { type: 'COUNTRY', country: city.country };
  const bounds = [
    [122, 20],
    [154, 46],
  ];
  assert.deepEqual(
    mapSearchResult(result, [mapCity(city)], [{ code: 'JP', bounds }]).bounds,
    bounds,
  );
  assert.deepEqual(mapSearchResult(result, [mapCity(city)], []).bounds, [
    [135.76811, 35.01161],
    [135.76811, 35.01161],
  ]);
});
test('출처 링크는 HTTP 또는 HTTPS만 허용함', () => {
  assert.equal(safeLink('javascript:alert(1)'), undefined);
  assert.equal(safeLink('data:text/html,a'), undefined);
  assert.equal(safeLink('https://example.org/'), 'https://example.org/');
});
test('API 실패나 잘못된 HTML 응답을 시연 데이터로 대체하지 않음', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('{}', { status: 404 }));
  await assert.rejects(getJson('/cities/1'), (error) => error.status === 404);
  globalThis.fetch = async () =>
    new Response('<html>fallback</html>', { headers: { 'Content-Type': 'text/html' } });
  await assert.rejects(getJson('/cities'), (error) => error.status === 502);
});
test('API 요청에 취소 신호를 전달하고 응답을 그대로 반환함', async (t) => {
  const controller = new AbortController();
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/cities');
    assert.equal(options.signal, controller.signal);
    return Response.json({ items: [city] });
  });
  assert.equal((await getJson('/cities', controller.signal)).items[0].id, city.id);
});

test('연결 실패와 잘못된 JSON은 안내 오류로 바꾸고 요청 취소는 유지함', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('Failed to fetch');
  });
  await assert.rejects(getJson('/cities'), (error) => error.status === 503);
  globalThis.fetch = async () =>
    new Response('invalid', { headers: { 'Content-Type': 'application/json' } });
  await assert.rejects(getJson('/cities'), (error) => error.status === 502);
  const controller = new AbortController();
  controller.abort();
  globalThis.fetch = async () => {
    throw controller.signal.reason;
  };
  await assert.rejects(
    getJson('/cities', controller.signal),
    (error) => error.name === 'AbortError',
  );
});

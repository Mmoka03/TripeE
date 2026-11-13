import test from 'node:test';
import assert from 'node:assert/strict';
import { clusterCities } from './cityClusters.js';

const point = (id, x, y, country = '일본') => ({ city: { id, country }, x, y });
test('인접한 도시는 국경과 무관하게 묶고 국가 수를 중복 없이 셈', () => {
  const result = clusterCities(
    [point('a', 0, 0), point('b', 10, 0), point('c', 20, 0, '중국')],
    64,
  );
  assert.equal(result.length, 1);
  assert.equal(result[0].cities.length, 3);
  assert.equal(result[0].countries, 2);
  assert.equal(result[0].x, 10);
});
test('확대하면 다시 분리되고 축소하면 원래 묶음으로 돌아감', () => {
  const points = [point('a', 0, 0), point('b', 40, 0)];
  assert.equal(clusterCities(points, 64).length, 1);
  assert.equal(
    clusterCities(
      points.map((p) => ({ ...p, x: p.x * 2 })),
      64,
    ).length,
    2,
  );
  assert.equal(clusterCities(points, 64).length, 1);
});
test('입력 순서와 지도 이동에 따라 묶음 구성이 바뀌지 않음', () => {
  const points = [point('a', 0, 0), point('b', 10, 0), point('c', 300, 10)];
  const keys = (p) =>
    clusterCities(p, 64)
      .map((g) => g.key)
      .sort();
  assert.deepEqual(
    keys(points),
    keys([...points].reverse().map((p) => ({ ...p, x: p.x + 124, y: p.y - 62 }))),
  );
});
test('모든 도시는 정확히 하나의 묶음에 속하며 겹친 좌표도 누락하지 않음', () => {
  const points = Array.from({ length: 245 }, (_, i) =>
    point(String(i), (i % 20) * 30, Math.floor(i / 20) * 30),
  );
  points[1].x = points[0].x;
  points[1].y = points[0].y;
  const ids = clusterCities(points, 64).flatMap((g) => g.cities.map((c) => c.id));
  assert.equal(ids.length, 245);
  assert.equal(new Set(ids).size, 245);
});
test('화면의 양 끝과 반경 밖 도시는 합치지 않음', () => {
  assert.equal(
    clusterCities([point('a', 0, 0), point('b', 64, 0), point('c', 1920, 0)], 64).length,
    3,
  );
  assert.deepEqual(clusterCities([], 64), []);
});

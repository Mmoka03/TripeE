import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import vm from 'node:vm';

const context = vm.createContext({});
vm.runInContext(
  readFileSync(new URL('../public/design/city-examples-data.js', import.meta.url), 'utf8'),
  context,
);
const { cities, selectExamples, isShortName } = context.TripeEIntro;

test('연속 방문 시 모든 예제 위치와 티켓 위치가 바뀌고 한 화면의 도시는 중복되지 않음', () => {
  let previous = {};
  for (let i = 0; i < 100; i++) {
    const examples = selectExamples(cities, previous);
    assert.equal(new Set(Object.values(examples).map((city) => city.id)).size, 3);
    for (const [slot, city] of Object.entries(examples)) assert.notEqual(city.id, previous[slot]);
    previous = Object.fromEntries(Object.entries(examples).map(([slot, city]) => [slot, city.id]));
  }
});

test('긴 도시명은 다른 섹션에 사용할 수 있어도 섹션 2 후보에서는 제외함', () => {
  const longCities = [
    { ...cities[0], id: 'long-ko', name: '샌프란시스코', english: 'SF' },
    { ...cities[0], id: 'long-en', name: '짧음', english: 'SanFrancisco' },
  ];
  for (const city of longCities) assert.equal(isShortName(city), false);
  for (let i = 0; i < 100; i++) {
    const examples = selectExamples([...cities, ...longCities]);
    assert.equal(isShortName(examples.bridge), true);
  }
});

test('오래되거나 손상된 저장값과 후보 부족을 처리함', () => {
  for (const previous of [null, 'broken', { bridge: 'removed', hero: 'removed' }]) {
    assert.ok(selectExamples(cities, previous));
  }
  assert.equal(selectExamples([]), null);
  assert.equal(selectExamples([cities[0]]), null);
  assert.equal(selectExamples(cities.map((city) => ({ ...city, name: '너무긴도시이름' }))), null);
});

test('모든 예제에 사진 파일, 출처, 이용 조건과 이동 대상이 있음', () => {
  assert.equal(new Set(cities.map((city) => city.id)).size, cities.length);
  for (const city of cities) {
    assert.ok(city.coordinates.every(Number.isFinite));
    assert.notEqual(city.photo.src, city.mood.src);
    for (const photo of [city.photo, city.mood]) {
      assert.ok(existsSync(new URL(`../public/design/${photo.src}`, import.meta.url)), photo.src);
      assert.ok(photo.alt.includes(city.name));
      assert.ok(photo.author && photo.license && photo.creditId);
      assert.equal(new URL(photo.source).protocol, 'https:');
      assert.equal(new URL(photo.licenseUrl).protocol, 'https:');
    }
  }
});

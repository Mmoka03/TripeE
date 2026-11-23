import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const data = JSON.parse(await readFile(new URL('./kyoto.json', import.meta.url), 'utf8'));
const doc = await readFile(new URL('../../docs/kyoto-content-draft.md', import.meta.url), 'utf8');
test('등록 문안과 좌표는 기존 검수본을 사용하며 경험 3개를 선정함', () => {
  assert.deepEqual(data.coordinates, { latitude: 35.01161, longitude: 135.76811 });
  assert.ok(doc.includes(data.tagline));
  const selected = data.contentItems.filter(
    (item) => item.kind === 'EXPERIENCE' && item.isSelected,
  );
  assert.equal(selected.length, 3);
  assert.match(
    selected.map((item) => item.title).join(' / '),
    /후시미이나리.*아라시야마.*니시키시장/,
  );
  for (const item of data.contentItems) {
    assert.ok(doc.includes(item.body));
    assert.ok(item.body.length <= 3000);
    assert.ok(item.evidenceNote.length <= 4000);
    assert.ok(item.sourceUrls.length);
    for (const url of item.sourceUrls) assert.ok(data.sources.some((source) => source.url === url));
  }
});
test('첫 사진과 권리 정보가 검수한 웹용 파일에 연결됨', async () => {
  const bytes = await readFile(new URL('../../' + data.photo.file, import.meta.url));
  assert.equal(createHash('sha256').update(bytes).digest('hex'), data.photo.sha256);
  assert.equal(data.photo.creatorName, 'Tomomarusan');
  assert.equal(data.photo.licenseName, 'CC BY 2.5');
  assert.ok(data.photo.altText);
  assert.match(data.photo.caption, /PNG 변환/);
});

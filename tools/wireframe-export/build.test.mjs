import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

test('배포 패키지의 가져오기 버튼이 화면과 실제 이미지 데이터를 전달함', async () => {
  const output = await mkdtemp(path.join(tmpdir(), 'tripee-wireframe-test-'));
  try {
    execFileSync(process.execPath, [
      fileURLToPath(new URL('./build.mjs', import.meta.url)),
      output,
    ]);
    const html = await readFile(path.join(output, 'ui.html'), 'utf8');
    assert.ok(!html.includes('__TRIPEE_WIREFRAME_DATA__'));
    const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
    const elements = { import: {}, 'update-fonts': {}, status: {}, scope: { value: 'all' } };
    const messages = [];
    const context = vm.createContext({
      document: { getElementById: (id) => elements[id] },
      parent: { postMessage: (message) => messages.push(message) },
      atob: (input) => Buffer.from(input, 'base64').toString('binary'),
      Uint8Array,
    });
    vm.runInContext(script, context);
    elements.import.onclick();
    const all = messages.at(-1).pluginMessage;
    assert.equal(all.type, 'import');
    assert.equal(all.data.frames.length, 21);
    assert.equal(Object.keys(all.data.images).length, 5);
    for (const [src, bytes] of Object.entries(all.data.images)) {
      assert.ok(bytes instanceof Uint8Array, src);
      assert.ok(bytes.length > 1000, src);
      assert.deepEqual([...bytes.slice(0, 2)], [255, 216], src);
    }
    assert.equal(elements.import.disabled, true);
    assert.equal(elements['update-fonts'].disabled, true);
    context.onmessage({ data: { pluginMessage: { type: 'done', text: '완료' } } });
    assert.equal(elements.import.disabled, false);
    assert.equal(elements['update-fonts'].disabled, false);
    elements.scope.value = 'mobile';
    elements.import.onclick();
    assert.equal(messages.at(-1).pluginMessage.data.frames.length, 7);
    assert.ok(
      messages.at(-1).pluginMessage.data.frames.every((frame) => frame.id.startsWith('mobile')),
    );
    context.onmessage({ data: { pluginMessage: { type: 'done', text: '완료' } } });
    const messageCount = messages.length;
    elements['update-fonts'].onclick();
    const update = messages.at(-1).pluginMessage;
    assert.equal(messages.length, messageCount + 1);
    assert.equal(update.type, 'update-fonts');
    assert.equal(update.data.frames.length, 21);
    assert.equal(Object.hasOwn(update.data, 'images'), false);
    assert.equal(elements.import.disabled, true);
    assert.equal(elements['update-fonts'].disabled, true);
    context.onmessage({ data: { pluginMessage: { type: 'error', text: '오류' } } });
    assert.equal(elements.import.disabled, false);
    assert.equal(elements['update-fonts'].disabled, false);
    new vm.Script(await readFile(path.join(output, 'code.js'), 'utf8'));
  } finally {
    // mkdtemp가 이번 검사에서 생성한 임시 폴더만 삭제함.
    assert.ok(path.basename(output).startsWith('tripee-wireframe-test-'));
    assert.equal(path.dirname(output), path.resolve(tmpdir()));
    await rm(output, { recursive: true, force: true });
  }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { checkBranch, checkSubject, checkTarget } from './check.mjs';

test('작업·릴리스·자동화 브랜치 형식을 검사함', () => {
  for (const name of [
    'main',
    'develop',
    'feature/12-city-search',
    'fix/edit-lock',
    'release/0.1.0',
    'hotfix/login',
    'codex/chore-git-conventions',
  ])
    assert.equal(checkBranch(name), true, name);
  for (const name of [
    'master',
    'feature/도시검색',
    'feature/City_Search',
    'feature/',
    'release/v1',
    'codex/random',
  ])
    assert.equal(checkBranch(name), false, name);
});
test('커밋 제목 형식과 글자 수를 검사함', () => {
  for (const title of [
    'feat: 도시 검색 추가',
    'fix(admin): 편집 잠금 복구',
    'refactor(api)!: 응답 구조 변경',
    'chore: 프로젝트 초기 구성',
  ])
    assert.equal(checkSubject(title), true, title);
  for (const title of [
    '수정함',
    'feat: ',
    'unknown: 수정',
    'fix: 수정\n본문',
    ' feat: 수정',
    'feat: ' + '가'.repeat(100),
  ])
    assert.equal(checkSubject(title), false, title);
});
test('작업 PR과 릴리스 PR의 병합 대상을 검사함', () => {
  assert.equal(checkTarget('feature/city-search', 'develop'), true);
  assert.equal(checkTarget('feature/city-search', 'main'), false);
  assert.equal(checkTarget('develop', 'main'), true);
  assert.equal(checkTarget('main', 'develop'), true);
  assert.equal(checkTarget('hotfix/login', 'main'), true);
  assert.equal(checkTarget('release/0.1.0', 'main'), true);
  assert.equal(checkTarget('feature/city-search', 'feature/other'), false);
});

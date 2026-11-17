import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const slug = '[a-z0-9]+(?:-[a-z0-9]+)*';
const workBranch = new RegExp(`^(feature|fix|refactor|docs|test|chore)/${slug}$`);
const automationBranch = new RegExp(`^codex/(feature|fix|refactor|docs|test|chore)-${slug}$`);
const releaseBranch = /^release\/\d+\.\d+\.\d+$/;
const hotfixBranch = new RegExp(`^hotfix/${slug}$`);
const subjectPattern =
  /^(feat|fix|docs|style|refactor|test|build|ci|chore|perf|revert)(\([a-z][a-z0-9-]*\))?!?: \S.*$/u;

export function checkBranch(name) {
  return (
    ['main', 'develop'].includes(name) ||
    workBranch.test(name) ||
    automationBranch.test(name) ||
    releaseBranch.test(name) ||
    hotfixBranch.test(name)
  );
}

export function checkSubject(subject) {
  return (
    subject === subject.trim() &&
    !/[\r\n]/.test(subject) &&
    [...subject].length <= 100 &&
    subjectPattern.test(subject)
  );
}

export function checkTarget(head, base) {
  if (!checkBranch(head)) return false;
  if (base === 'develop') return head !== 'develop';
  if (base === 'main')
    return head === 'develop' || releaseBranch.test(head) || hotfixBranch.test(head);
  return false;
}

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();

export function run(args) {
  const [mode, value, target] = args;
  if (mode === 'branch') {
    if (!checkBranch(value || git('branch', '--show-current')))
      throw Error('브랜치 이름이 규칙에 맞지 않음. CONTRIBUTING.md를 확인함.');
  } else if (mode === 'commit' || mode === 'title') {
    // 본문은 자유롭게 작성하며 제목만 검사함. Git이 남긴 주석은 검사에서 제외함.
    const subject =
      mode === 'commit'
        ? readFileSync(value, 'utf8')
            .split(/\r?\n/)
            .find((line) => line && !line.startsWith('#')) || ''
        : value;
    if (!checkSubject(subject || ''))
      throw Error('제목은 type(scope): 내용 형식, 100자 이하로 작성함.');
  } else if (mode === 'pr') {
    if (!checkTarget(value, target))
      throw Error('PR 병합 대상이 규칙에 맞지 않음. 작업 브랜치는 develop을 대상으로 함.');
  } else if (mode === 'range') {
    if (!/^[a-f0-9]{40}\.\.[a-f0-9]{40}$/.test(value || ''))
      throw Error('커밋 검사 범위가 올바르지 않음.');
    const commits = git('rev-list', value).split('\n').filter(Boolean);
    for (const sha of commits) {
      // Gitflow에서 생성한 병합 커밋은 제외하고 일반 커밋 제목을 검사함.
      if (git('show', '-s', '--format=%P', sha).split(' ').filter(Boolean).length > 1) continue;
      if (!checkSubject(git('show', '-s', '--format=%s', sha)))
        throw Error(`${sha.slice(0, 7)} 커밋 제목이 규칙에 맞지 않음.`);
    }
  } else throw Error('branch, commit, title, pr, range 중 검사 방식을 지정함.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    run(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

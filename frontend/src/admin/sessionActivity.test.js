import test from 'node:test';
import assert from 'node:assert/strict';
import { createActivityReporter, sessionDeadline } from './sessionActivity.js';

function clock() {
  let time = 0;
  let id = 0;
  const timers = new Map();
  return {
    options: {
      now: () => time,
      schedule: (fn, delay) => {
        timers.set(++id, { at: time + delay, fn });
        return id;
      },
      cancel: (timer) => timers.delete(timer),
    },
    tick(ms) {
      time += ms;
      for (const [key, timer] of timers)
        if (timer.at <= time) {
          timers.delete(key);
          timer.fn();
        }
    },
  };
}

test('첫 활동은 즉시, 연속 입력은 30초에 한 번 마지막 동작을 보고함', () => {
  const timers = clock();
  const sent = [];
  const reporter = createActivityReporter((event) => sent.push(event), timers.options);
  reporter.report('CLICK');
  timers.tick(1_000);
  reporter.report('CLICK');
  reporter.report('INPUT');
  assert.deepEqual(sent, ['CLICK']);
  timers.tick(29_000);
  assert.deepEqual(sent, ['CLICK', 'INPUT']);
});

test('사용자 동작이 없으면 자동 연장하지 않고 화면 종료 시 예약 보고도 취소함', () => {
  const timers = clock();
  const sent = [];
  const reporter = createActivityReporter((event) => sent.push(event), timers.options);
  reporter.report('CLICK');
  timers.tick(3_600_000);
  assert.equal(sent.length, 1);
  reporter.report('INPUT');
  reporter.report('CLICK');
  reporter.dispose();
  timers.tick(60_000);
  assert.deepEqual(sent, ['CLICK', 'INPUT']);
});

test('사용자 PC 시계가 달라도 서버 시각 기준 남은 시간으로 만료를 계산함', () => {
  assert.equal(
    sessionDeadline(
      { serverTime: '2026-11-23T00:00:00Z', idleExpiresAt: '2026-11-23T01:00:00Z' },
      1_000,
    ),
    3_601_000,
  );
  assert.equal(
    sessionDeadline(
      { serverTime: '2026-11-23T01:00:01Z', idleExpiresAt: '2026-11-23T01:00:00Z' },
      1_000,
    ),
    1_000,
  );
});

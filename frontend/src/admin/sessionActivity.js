// 반복 클릭은 묶되 마지막 사용자 동작을 누락하지 않도록 후행 전송함.
// 동작이 없는 동안에는 세션 연장 요청을 만들지 않음.
export function createActivityReporter(
  send,
  { interval = 30_000, now = Date.now, schedule = setTimeout, cancel = clearTimeout } = {},
) {
  let lastSent = -Infinity;
  let pending = null;
  let timer = null;
  function flush() {
    timer = null;
    if (!pending) return;
    const event = pending;
    pending = null;
    lastSent = now();
    send(event);
  }
  return {
    report(event) {
      pending = event;
      if (timer !== null) return;
      const delay = interval - (now() - lastSent);
      if (delay <= 0) flush();
      else timer = schedule(flush, delay);
    },
    dispose() {
      if (timer !== null) cancel(timer);
      timer = null;
      pending = null;
    },
  };
}

export function sessionDeadline(session, now = Date.now()) {
  return now + Math.max(0, Date.parse(session.idleExpiresAt) - Date.parse(session.serverTime));
}

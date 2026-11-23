export class AdminApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const messages = {
  INVALID_CREDENTIALS: '아이디 또는 비밀번호를 확인해주세요.',
  SESSION_EXPIRED: '로그인 시간이 만료됐어요. 다시 로그인해주세요.',
  AUTH_REQUIRED: '관리자 로그인이 필요해요.',
  CSRF_INVALID: '로그인 상태를 다시 확인해야 해요. 한 번 더 시도해주세요.',
  FORBIDDEN: '이 작업에 접근할 권한이 없어요.',
};

export function createAdminApi(fetchImpl = globalThis.fetch) {
  // 비밀번호와 CSRF 토큰을 브라우저 저장소에 기록하지 않음. 세션 쿠키는 서버가 관리함.
  let csrf = null;

  async function request(path, { method = 'GET', body, signal } = {}) {
    const headers = { Accept: 'application/json' };
    if (method !== 'GET') {
      if (!csrf) csrf = await request('/csrf', { signal });
      headers[csrf.headerName] = csrf.token;
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetchImpl(`/api/v1/admin${path}`, {
        method,
        headers,
        signal,
        credentials: 'same-origin',
        cache: 'no-store',
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
    } catch (error) {
      if (signal?.aborted || error.name === 'AbortError') throw error;
      throw new AdminApiError(
        503,
        'NETWORK_ERROR',
        '서버에 연결하지 못했어요. 연결 상태를 확인하고 다시 시도해주세요.',
      );
    }
    if (!response.ok) {
      let problem;
      try {
        problem = await response.json();
      } catch {
        /* 빈 오류 응답도 같은 형식으로 처리함. */
      }
      const code = problem?.code || 'REQUEST_FAILED';
      if (response.status === 401 || code === 'CSRF_INVALID') csrf = null;
      throw new AdminApiError(
        response.status,
        code,
        messages[code] ||
          (response.status === 404
            ? '도시를 찾을 수 없어요. 목록을 다시 확인해주세요.'
            : '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.'),
      );
    }
    if (response.status === 204) return null;
    if (!response.headers.get('content-type')?.includes('application/json')) {
      throw new AdminApiError(
        502,
        'INVALID_RESPONSE',
        '서버 응답을 확인하지 못했어요. 다시 시도해주세요.',
      );
    }
    try {
      return await response.json();
    } catch (error) {
      if (signal?.aborted || error.name === 'AbortError') throw error;
      throw new AdminApiError(
        502,
        'INVALID_RESPONSE',
        '서버 응답을 확인하지 못했어요. 다시 시도해주세요.',
      );
    }
  }

  return {
    session: (signal) => request('/session', { signal }),
    async login(loginId, password, signal) {
      const session = await request('/session', {
        method: 'POST',
        body: { loginId, password },
        signal,
      });
      // 로그인 시 서버가 세션과 CSRF 토큰을 교체하므로 다음 변경 요청 전에 다시 발급받음.
      csrf = null;
      return session;
    },
    async logout(signal) {
      await request('/session', { method: 'DELETE', signal });
      csrf = null;
    },
    activity: (event, signal) =>
      request('/session/activity', { method: 'POST', body: { event }, signal }),
    cities({ q = '', visibility = '', cursor, signal } = {}) {
      const query = new URLSearchParams({ limit: '20' });
      if (q.trim()) query.set('q', q.trim());
      if (visibility) query.set('visibility', visibility);
      if (cursor) query.set('cursor', cursor);
      return request(`/cities?${query}`, { signal });
    },
    city: (id, signal) => request(`/cities/${encodeURIComponent(id)}`, { signal }),
  };
}

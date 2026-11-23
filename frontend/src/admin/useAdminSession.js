import { useCallback, useEffect, useRef, useState } from 'react';
import { createActivityReporter, sessionDeadline } from './sessionActivity';

export function useAdminSession(api) {
  const [state, setState] = useState({ status: 'checking', session: null, deadline: 0 });
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const epoch = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      epoch.current++;
    };
  }, []);

  const accept = useCallback((session, generation) => {
    if (!mounted.current || generation !== epoch.current) return;
    setState((previous) => {
      // 다른 탭 확인 요청과 활동 보고가 엇갈려도 이전 만료 시간으로 되돌리지 않음.
      if (
        previous.session?.admin.id === session.admin.id &&
        Date.parse(previous.session.lastActivityAt) > Date.parse(session.lastActivityAt)
      )
        return previous;
      return { status: 'authenticated', session, deadline: sessionDeadline(session) };
    });
    setNotice('');
  }, []);

  const requireLogin = useCallback((message = '') => {
    epoch.current++;
    setState({ status: 'anonymous', session: null, deadline: 0 });
    setNotice(message);
    setBusy(false);
  }, []);

  const check = useCallback(
    async (signal, initial = false) => {
      const generation = epoch.current;
      try {
        accept(await api.session(signal), generation);
      } catch (error) {
        if (signal?.aborted || !mounted.current || generation !== epoch.current) return;
        if (error.status === 401) requireLogin(initial ? '' : error.message);
        else {
          // 인증 여부를 확인할 수 없으면 관리자 데이터를 계속 노출하지 않음.
          epoch.current++;
          setState({ status: 'error', session: null, deadline: 0 });
          setNotice(error.message);
        }
      }
    },
    [api, accept, requireLogin],
  );

  useEffect(() => {
    const controller = new AbortController();
    check(controller.signal, true);
    return () => controller.abort();
  }, [check]);

  useEffect(() => {
    if (state.status !== 'authenticated') return;
    const controller = new AbortController();
    const generation = epoch.current;
    const reporter = createActivityReporter(async (event) => {
      try {
        accept(await api.activity(event, controller.signal), generation);
      } catch (error) {
        if (controller.signal.aborted || generation !== epoch.current) return;
        if (error.status === 401) requireLogin(error.message);
        else setNotice(`활동 상태를 확인하지 못했어요. ${error.message}`);
      }
    });
    function activity(event) {
      if (!event.isTrusted) return;
      reporter.report(event.type === 'click' ? 'CLICK' : 'INPUT');
    }
    const visible = () => {
      if (document.visibilityState === 'visible') check(controller.signal);
    };
    document.addEventListener('click', activity);
    document.addEventListener('input', activity);
    document.addEventListener('keydown', activity);
    document.addEventListener('visibilitychange', visible);
    return () => {
      controller.abort();
      reporter.dispose();
      document.removeEventListener('click', activity);
      document.removeEventListener('input', activity);
      document.removeEventListener('keydown', activity);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [api, state.status, accept, check, requireLogin]);

  useEffect(() => {
    if (state.status !== 'authenticated') return;
    const controller = new AbortController();
    // PC의 시계 오차를 피하기 위해 서버가 반환한 남은 시간을 사용함.
    const timer = setTimeout(
      () => check(controller.signal),
      Math.max(0, state.deadline - Date.now()) + 100,
    );
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [state.status, state.deadline, check]);

  async function login(loginId, password) {
    const generation = ++epoch.current;
    setBusy(true);
    setNotice('');
    try {
      accept(await api.login(loginId, password), generation);
    } catch (error) {
      if (mounted.current && generation === epoch.current) setNotice(error.message);
    } finally {
      if (mounted.current && generation === epoch.current) setBusy(false);
    }
  }

  async function logout() {
    const generation = ++epoch.current;
    // 로그아웃과 활동 요청이 동시에 실행되지 않도록 활동 수집부터 중단함.
    setState((value) => ({ ...value, status: 'logging-out' }));
    setBusy(true);
    setNotice('');
    try {
      await api.logout();
      if (mounted.current && generation === epoch.current) requireLogin();
    } catch (error) {
      if (!mounted.current || generation !== epoch.current) return;
      if (error.status === 401) requireLogin();
      else {
        setState((value) => ({ ...value, status: 'authenticated' }));
        setNotice(`로그아웃을 완료하지 못했어요. ${error.message}`);
      }
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  const onUnauthorized = useCallback((error) => requireLogin(error.message), [requireLogin]);
  const retry = () => {
    setState({ status: 'checking', session: null, deadline: 0 });
    check();
  };
  return { ...state, notice, busy, login, logout, retry, onUnauthorized };
}

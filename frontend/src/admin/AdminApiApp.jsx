import React, { useMemo, useState } from 'react';
import { createAdminApi } from './adminApi';
import { useAdminSession } from './useAdminSession';
import { Field } from './AdminFields';
import AdminCityBrowser from './AdminCityBrowser';
import './admin.css';
import './admin-api.css';

function LoginForm({ session }) {
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  return (
    <main className="adm-login">
      <section>
        <span className="adm-eyebrow">TRIPEE ADMIN</span>
        <h1>
          여행의 정보를
          <br />
          함께 관리해요.
        </h1>
        <p>
          관리자 계정으로 로그인하고
          <br />
          등록된 도시와 공개 상태를 확인하세요.
        </p>
        <a href="/">지도로 돌아가기 →</a>
      </section>
      <form
        className="adm-card"
        onSubmit={(event) => {
          event.preventDefault();
          session.login(loginId.trim(), password);
          setPassword('');
        }}
      >
        <h2>관리자 로그인</h2>
        <Field label="아이디">
          <input
            name="username"
            autoComplete="username"
            value={loginId}
            onChange={(event) => setLoginId(event.target.value)}
            required
            maxLength={64}
            disabled={session.busy}
            autoCapitalize="none"
            spellCheck={false}
          />
        </Field>
        <Field label="비밀번호">
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            maxLength={1024}
            disabled={session.busy}
          />
        </Field>
        {session.notice && (
          <p className="adm-message error" role="alert">
            {session.notice}
          </p>
        )}
        <button className="adm-primary" type="submit" disabled={session.busy}>
          {session.busy ? '로그인 중…' : '로그인'}
        </button>
        <small className="adm-hint">관리자가 발급한 계정으로 로그인할 수 있어요.</small>
      </form>
    </main>
  );
}

export default function AdminApiApp() {
  const api = useMemo(() => createAdminApi(), []);
  const session = useAdminSession(api);
  return (
    <div className="admin-app admin-api-app">
      {session.status === 'checking' ? (
        <main className="adm-api-status">
          <p role="status">로그인 상태를 확인하고 있어요.</p>
        </main>
      ) : session.status === 'error' ? (
        <main className="adm-api-status">
          <h1>관리자 화면에 연결하지 못했어요</h1>
          <p role="alert">{session.notice}</p>
          <button onClick={session.retry}>다시 시도</button>
          <a href="/">지도로 돌아가기</a>
        </main>
      ) : session.status === 'anonymous' ? (
        <LoginForm session={session} />
      ) : (
        <>
          <header className="adm-header">
            <a className="adm-logo" href="/">
              TripeE <span>관리자</span>
            </a>
            <div className="adm-account">
              <span>{session.session.admin.displayName}</span>
              <button disabled={session.busy} onClick={session.logout}>
                {session.busy ? '로그아웃 중…' : '로그아웃'}
              </button>
            </div>
          </header>
          <main className="adm-main">
            <div className="adm-page-heading">
              <div>
                <span className="adm-eyebrow">CITY MANAGEMENT</span>
                <h1>도시 관리</h1>
                <p>등록된 도시와 게시 상태를 확인하세요.</p>
              </div>
            </div>
            <p className="adm-hint">입력이나 클릭 없이 1시간이 지나면 로그인이 만료돼요.</p>
            {session.notice && (
              <p className="adm-message error" role="alert">
                {session.notice}
              </p>
            )}
            {session.status === 'logging-out' ? (
              <p role="status">로그아웃하고 있어요.</p>
            ) : (
              <AdminCityBrowser api={api} onUnauthorized={session.onUnauthorized} />
            )}
          </main>
        </>
      )}
    </div>
  );
}

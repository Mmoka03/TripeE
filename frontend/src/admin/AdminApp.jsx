import React, { useEffect, useRef, useState } from 'react';
import Icon from '../Icon';
import {
  blankSafety,
  createDemoService,
  kindNames,
  publicationProblems,
  safetyProblems,
} from './demoService';
import { Field, Badge, Empty, SafetyForm } from './AdminFields';
import './admin.css';

const labels = { PUBLIC: '공개', PREPARING: '정보 준비 중', HIDDEN: '비공개' };
const actions = {
  PUBLISH: '도시 전체 게시',
  SAFETY_PUBLISH: '안전정보 게시',
  UPDATE_DRAFT: '초안 저장',
  CREATE_DRAFT: '초안 생성',
  DISCARD_DRAFT: '초안 폐기',
  SET_VISIBILITY: '공개 상태 변경',
  DELETE: '도시 삭제',
};
const time = (value) =>
  value ? new Date(value).toLocaleString('ko-KR', { hour12: false }) : '저장 전';
const copy = (value) => structuredClone(value);

export default function AdminApp() {
  const [service] = useState(() => createDemoService(localStorage));
  const [state, setState] = useState(() => {
    try {
      return service.read();
    } catch {
      return { cities: [], events: [], loadError: true };
    }
  });
  const [user, setUser] = useState(null),
    [choice, setChoice] = useState('A'),
    [view, setView] = useState('cities'),
    [cityId, setCityId] = useState(null);
  const [draft, setDraft] = useState(null),
    [tab, setTab] = useState('basic'),
    [dirty, setDirty] = useState(false),
    [message, setMessage] = useState(null),
    [modal, setModal] = useState(null);
  const [safety, setSafety] = useState(blankSafety()),
    [safetyBase, setSafetyBase] = useState(null),
    [reviewed, setReviewed] = useState(false),
    [search, setSearch] = useState(''),
    [filter, setFilter] = useState('ALL');
  const [eventId, setEventId] = useState(null),
    [country, setCountry] = useState('JP'),
    [tick, setTick] = useState(Date.now());
  useEffect(() => {
    const title = document.title;
    document.title = 'TripeE Admin · 콘텐츠 관리 시연';
    return () => {
      document.title = title;
    };
  }, []);
  const context = useRef({
      user: null,
      editor: crypto.randomUUID(),
      token: null,
      lastActivity: Date.now(),
    }),
    dialog = useRef(null),
    savedFocus = useRef(null),
    uploads = useRef(new Map());
  const c = state.cities.find((x) => x.id === cityId),
    editView = ['edit', 'preview', 'conflict'].includes(view),
    ctx = () => ({ ...context.current });
  const validLease = Boolean(
    c?.lock &&
      c.lock.editor === context.current.editor &&
      c.lock.token === context.current.token &&
      c.lock.expiresAt > tick,
  );
  const notify = (text, error = false) => setMessage({ text, error });
  const refresh = () => {
    const s = service.read();
    setState(s);
    setTick(Date.now());
    return s;
  };
  const run = (fn) => {
    try {
      return fn();
    } catch (e) {
      notify(e.message || '처리하지 못했어요.', true);
      if (e.code === 'SESSION_EXPIRED') {
        context.current.user = null;
        setUser(null);
      }
      return null;
    }
  };
  const release = () => {
    if (cityId && context.current.token) run(() => service.release(cityId, ctx()));
    context.current.token = null;
  };
  const ask = (title, body, action, danger = false, confirmText = '확인') =>
    setModal({ title, body, action, danger, confirmText });
  // 내부 이동 시 미저장 변경을 확인하고 기존 편집권을 반납함.
  const navigate = (fn) => {
    const go = () => {
      release();
      setDirty(false);
      setMessage(null);
      fn();
      refresh();
    };
    if (dirty)
      ask(
        '저장하지 않은 변경이 있어요',
        '이동하면 현재 화면의 미저장 변경을 잃습니다. 저장된 초안은 유지됩니다.',
        go,
        false,
        '변경을 버리고 이동',
      );
    else go();
  };
  const beginEdit = (id) =>
    navigate(() => {
      const result = run(() => service.acquire(id, ctx()));
      if (!result) return;
      context.current.token = result.lock.token;
      setCityId(id);
      setDraft(result.draft);
      setTab('basic');
      setView('edit');
      setTick(Date.now());
    });
  // 새로고침·재진입으로 남은 본인 잠금을 명시적으로 재개함. 미저장 입력은 복구하지 않음.
  const resumeEdit = (city) =>
    ask(
      '본인 편집권을 재개할까요?',
      '이전에 열었던 화면의 편집권을 종료하고, 마지막으로 저장된 초안을 이 화면에서 엽니다. 이전 화면의 미저장 입력은 가져오지 않습니다.',
      () =>
        navigate(() => {
          const result = run(() => service.reclaim(city.id, city.lock.token, ctx()));
          if (!result) {
            refresh();
            return;
          }
          context.current.token = result.lock.token;
          setCityId(city.id);
          setDraft(result.draft);
          setTab('basic');
          setView('edit');
          notify('편집을 재개했어요. 이전 화면에서는 저장할 수 없습니다.');
        }),
      false,
      '이 화면에서 편집 재개',
    );
  const beginSafety = (id) =>
    navigate(() => {
      const latest = service.read().cities.find((x) => x.id === id);
      if (!latest) return;
      setCityId(id);
      setSafety(copy(latest.safety));
      setSafetyBase(latest.safety.id);
      setView('safety');
    });
  const updateDraft = (key, value) => {
    setDraft((d) => ({ ...d, [key]: value }));
    setDirty(true);
  };
  const save = () =>
    run(() => {
      const saved = service.save(cityId, draft, ctx());
      setDraft(saved);
      setDirty(false);
      refresh();
      notify('초안을 저장했어요. 공개본은 변경되지 않았습니다.');
      return saved;
    });
  const preview = () => {
    const saved = dirty ? save() : draft;
    if (saved) {
      setView('preview');
      setMessage(null);
      refresh();
    }
  };
  const publish = () =>
    ask(
      '도시 전체를 게시할까요?',
      '저장된 일반 콘텐츠 전체와 필요한 안전정보를 함께 게시합니다. 게시 성공 후 편집권을 반납합니다.',
      () =>
        run(() => {
          setState(service.publish(cityId, draft, ctx()));
          context.current.token = null;
          setDirty(false);
          setView('cities');
          notify('도시 전체를 게시했어요. 시연 데이터에만 반영됩니다.');
        }),
      false,
      '전체 게시',
    );
  const conflict = Boolean(draft?.safetyDraft && draft.baseSafetyId !== c?.safety?.id);
  const problems = publicationProblems(draft);
  useEffect(() => {
    const activity = (e) => {
      if (
        e.isTrusted &&
        context.current.user &&
        Date.now() - context.current.lastActivity < 3600000
      )
        context.current.lastActivity = Date.now();
    };
    document.addEventListener('pointerdown', activity);
    document.addEventListener('keydown', activity);
    const timer = setInterval(() => {
      const now = Date.now();
      setTick(now);
      if (context.current.user && now - context.current.lastActivity >= 3600000) {
        context.current.user = null;
        setUser(null);
        notify('비활동 세션이 만료됐어요. 열린 화면의 입력은 유지됩니다.', true);
      }
      try {
        setState(service.read());
      } catch {}
    }, 5000);
    const storage = (e) => {
      if (e.key === 'tripee.admin-demo.v1') run(refresh);
    };
    window.addEventListener('storage', storage);
    return () => {
      clearInterval(timer);
      document.removeEventListener('pointerdown', activity);
      document.removeEventListener('keydown', activity);
      window.removeEventListener('storage', storage);
    };
  }, []);
  useEffect(() => {
    if (!user || !editView) return;
    const interval = setInterval(
      () =>
        run(() => {
          service.heartbeat(cityId, ctx());
          refresh();
        }),
      60000,
    );
    return () => clearInterval(interval);
  }, [user, view, cityId]);
  useEffect(() => {
    const before = (e) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, [dirty]);
  useEffect(() => {
    setReviewed(false);
  }, [c?.safety?.id]);
  useEffect(() => {
    if (!modal) return;
    savedFocus.current = document.activeElement;
    dialog.current?.querySelector('button')?.focus();
    return () => savedFocus.current?.isConnected && savedFocus.current.focus();
  }, [modal]);
  useEffect(
    () => () => {
      uploads.current.forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );
  const login = () => {
    context.current.user = choice;
    context.current.lastActivity = Date.now();
    context.current.token = null;
    setUser(choice);
    if (!draft) setView('cities');
    else {
      setView('edit');
      notify('입력은 유지됐어요. 편집권을 다시 얻고 최신 초안을 확인해주세요.', true);
    }
    refresh();
  };
  // 시연에서는 필드 충족만 확인함. 실제 근거 대조와 확인 책임은 관리자에게 있음.
  const sourceCheck = (item) => {
    const next = { ...item };
    if (
      item.title.trim() &&
      item.body.trim() &&
      /^https?:\/\/\S+$/i.test(item.sourceUrl) &&
      item.evidence.trim()
    )
      next.confirmed = true;
    else notify('제목·본문·출처 URL·근거 메모를 먼저 입력해주세요.', true);
    return next;
  };
  const patchContent = (index, key, value) =>
    updateDraft(
      'contentItems',
      draft.contentItems.map((item, i) =>
        i === index
          ? {
              ...item,
              [key]: value,
              ...(['title', 'body', 'kind', 'sourceUrl', 'evidence'].includes(key)
                ? { confirmed: false }
                : {}),
            }
          : item,
      ),
    );
  const reorder = (key, index, direction) => {
    const items = [...draft[key]],
      other = index + direction;
    if (other < 0 || other >= items.length) return;
    [items[index], items[other]] = [items[other], items[index]];
    updateDraft(
      key,
      items.map((x, i) => ({ ...x, order: i + 1 })),
    );
  };
  const addPhoto = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (
      !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
      file.size > 10 * 1024 * 1024
    )
      return notify('JPEG·PNG·WebP 형식의 10MiB 이하 사진을 선택해주세요.', true);
    try {
      const bitmap = await createImageBitmap(file);
      const pixels = bitmap.width * bitmap.height;
      bitmap.close();
      if (pixels > 40000000) return notify('4천만 픽셀 이하의 사진을 선택해주세요.', true);
      const photoId = crypto.randomUUID();
      uploads.current.set(photoId, URL.createObjectURL(file));
      updateDraft('photos', [
        ...draft.photos,
        {
          id: photoId,
          name: file.name,
          alt: '',
          sourceUrl: '',
          creator: '',
          license: '',
          rights: false,
          previewUrl: null,
        },
      ]);
    } catch {
      notify('사진 파일을 읽을 수 없어요.', true);
    }
  };
  const screenTitle =
    view === 'cities'
      ? '도시 관리'
      : view === 'history'
        ? '감사 이력'
        : view === 'safety'
          ? '안전정보 독립 편집'
          : view === 'preview'
            ? '게시 미리보기'
            : view === 'conflict'
              ? '안전정보 재검토'
              : '일반 초안 편집';
  const visibleCities = state.cities.filter(
    (x) =>
      (filter === 'ALL' || x.visibility === filter) &&
      `${x.nameKo} ${x.nameEn} ${x.country} ${x.draft?.nameKo || ''}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  const latestEvents = state.events.filter(
    (e) => !search || `${e.cityName} ${e.cityId} ${actions[e.action]}`.includes(search),
  );
  const selectedEvent = latestEvents.find((e) => e.id === eventId) || latestEvents[0];
  const copyInput = async () => {
    try {
      await navigator.clipboard.writeText(JSON.stringify(draft, null, 2));
      notify('현재 초안을 복사했어요.');
    } catch {
      notify('클립보드에 접근할 수 없어요. 브라우저에서 입력을 선택해 복사해주세요.', true);
    }
  };
  return (
    <div className="admin-app">
      <div className="adm-demo-banner">
        <strong>로컬 시연 모드</strong>
        <span>실제 인증·서버·DB 연결 전입니다. 민감한 자료를 입력하지 마세요.</span>
        <a href="/">
          사용자 지도 보기 <Icon name="arrow" />
        </a>
      </div>
      {!user ? (
        <main className="adm-login">
          <section>
            <span className="adm-eyebrow">TRIPEE / CONTENT WORKSPACE</span>
            <h1>
              도시의 이야기를
              <br />
              함께 준비하세요.
            </h1>
            <p>
              초안부터 검토와 게시까지,
              <br />
              관리자의 작업 흐름을 확인하는 공간입니다.
            </p>
            <div className="adm-notice">
              비밀번호를 요구하지 않는 시연용 관리자 선택입니다. 실제 로그인 기능이 아닙니다.
            </div>
          </section>
          <section className="adm-card">
            <h2>시연 관리자 선택</h2>
            <p className="adm-hint">각 탭에서 다른 관리자를 선택해 잠금 흐름을 확인할 수 있어요.</p>
            <div className="adm-personas">
              {['A', 'B'].map((v) => (
                <label key={v}>
                  <input
                    type="radio"
                    name="persona"
                    checked={choice === v}
                    onChange={() => setChoice(v)}
                  />
                  <strong>관리자 {v}</strong>
                  <small>동일한 콘텐츠 관리 권한</small>
                </label>
              ))}
            </div>
            {message && <div className="adm-notice">{message.text}</div>}
            <button className="adm-primary" onClick={login}>
              관리자 {choice}로 시연 시작
            </button>
            <small className="adm-hint">
              시연 데이터는 현재 브라우저에 저장됩니다. 새로고침 시 시연 관리자 선택이 필요합니다.
            </small>
          </section>
        </main>
      ) : (
        <>
          <header className="adm-header">
            <a className="adm-logo" href="/admin">
              TripeE <span>Admin</span>
            </a>
            <nav aria-label="관리자 메뉴">
              <button
                className={view === 'cities' ? 'active' : ''}
                onClick={() =>
                  navigate(() => {
                    setView('cities');
                    setSearch('');
                  })
                }
              >
                도시 관리
              </button>
              <button
                className={view === 'history' ? 'active' : ''}
                onClick={() =>
                  navigate(() => {
                    setView('history');
                    setSearch('');
                  })
                }
              >
                감사 이력
              </button>
            </nav>
            <div className="adm-account">
              <span>관리자 {user}</span>
              <button
                onClick={() =>
                  navigate(() => {
                    context.current.user = null;
                    setUser(null);
                    setDraft(null);
                  })
                }
              >
                나가기
              </button>
            </div>
          </header>
          <main className="adm-main">
            <div className="adm-page-heading">
              <div>
                <span className="adm-eyebrow">CONTENT MANAGEMENT</span>
                <h1>{screenTitle}</h1>
                {c && view !== 'cities' && view !== 'history' && (
                  <p>
                    {c.nameKo || draft?.nameKo || '새 도시'} · {c.country}{' '}
                    <Badge>{labels[c.visibility]}</Badge>
                  </p>
                )}
              </div>
              <div className="adm-actions">
                {view === 'cities' && (
                  <button
                    className="adm-primary"
                    onClick={() =>
                      ask(
                        '새 도시 추가',
                        '국가를 선택하면 비공개 도시와 미완성 초안을 만듭니다.',
                        (selectedCountry) =>
                          run(() => {
                            const id = service.create(selectedCountry, ctx());
                            refresh();
                            beginEdit(id);
                          }),
                        false,
                        '비공개 초안 만들기',
                      )
                    }
                  >
                    ＋ 도시 추가
                  </button>
                )}
                {view !== 'cities' && (
                  <button
                    onClick={() =>
                      navigate(() => {
                        setView('cities');
                        setSearch('');
                      })
                    }
                  >
                    도시 목록
                  </button>
                )}
              </div>
            </div>
            {message && (
              <div
                className={`adm-message ${message.error ? 'error' : ''}`}
                role={message.error ? 'alert' : 'status'}
              >
                <span>{message.text}</span>
                <button
                  className="adm-icon"
                  aria-label="안내 닫기"
                  onClick={() => setMessage(null)}
                >
                  <Icon name="close" />
                </button>
              </div>
            )}
            {state.loadError && (
              <div className="adm-notice">
                시연 데이터를 읽지 못했어요. 하단에서 시연 데이터를 초기화해주세요.
              </div>
            )}
            {view === 'cities' && (
              <>
                <div className="adm-stats">
                  {[
                    ['전체 도시', state.cities.length],
                    ['공개', state.cities.filter((x) => x.visibility === 'PUBLIC').length],
                    ['작성 중인 초안', state.cities.filter((x) => x.draft).length],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <span>{label}</span>
                      <strong>
                        {value}
                        <small>개</small>
                      </strong>
                    </div>
                  ))}
                </div>
                <div className="adm-card">
                  <div className="adm-list-tools">
                    <Field label="도시 검색">
                      <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="도시명·국가명 검색"
                      />
                    </Field>
                    <Field label="공개 상태">
                      <select value={filter} onChange={(e) => setFilter(e.target.value)}>
                        <option value="ALL">전체 상태</option>
                        {Object.entries(labels).map(([value, name]) => (
                          <option key={value} value={value}>
                            {name}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <span className="adm-hint">{visibleCities.length}개 도시</span>
                  </div>
                  <div className="adm-table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>도시 / 국가</th>
                          <th>공개 상태</th>
                          <th>초안 / 편집권</th>
                          <th>작업</th>
                        </tr>
                      </thead>
                      <tbody>
                        {visibleCities.map((city) => {
                          const held =
                            city.lock &&
                            city.lock.expiresAt > tick &&
                            city.lock.editor !== context.current.editor;
                          return (
                            <tr key={city.id}>
                              <td>
                                <strong>
                                  {city.nameKo || city.draft?.nameKo || '이름 없는 도시'}
                                </strong>
                                <small>
                                  {city.nameEn || city.draft?.nameEn || '—'} · {city.country}
                                </small>
                              </td>
                              <td>
                                <Badge tone={city.visibility === 'PUBLIC' ? 'green' : ''}>
                                  {labels[city.visibility]}
                                </Badge>
                              </td>
                              <td>
                                <span>
                                  {city.draft ? '수정 초안 있음' : '공개본으로 초안 생성'}
                                </span>
                                <small>
                                  {held
                                    ? `${city.lock.owner} 편집 중 · ${Math.max(1, Math.ceil((city.lock.expiresAt - tick) / 60000))}분 이내 만료`
                                    : '편집 가능'}
                                </small>
                              </td>
                              <td>
                                <div className="adm-actions">
                                  <button
                                    disabled={held && city.lock.owner !== user}
                                    onClick={() => (held ? resumeEdit(city) : beginEdit(city.id))}
                                  >
                                    {held && city.lock.owner === user ? '편집 재개' : '초안 편집'}
                                  </button>
                                  <button onClick={() => beginSafety(city.id)}>안전정보</button>
                                  <button
                                    onClick={() =>
                                      navigate(() => {
                                        setSearch(city.id);
                                        setView('history');
                                      })
                                    }
                                  >
                                    이력
                                  </button>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  {!visibleCities.length && (
                    <Empty title="도시가 없어요">
                      검색 조건을 바꾸거나 새 도시를 추가해주세요.
                    </Empty>
                  )}
                </div>
                <div className="adm-notice">
                  일반 초안이 잠겨 있어도 안전정보는 별도로 편집·게시할 수 있어요. 시연의 나폴리는
                  B가 편집 중인 예시입니다.
                </div>
              </>
            )}
            {editView && draft && c && (
              <>
                <div className={`adm-notice ${!validLease ? 'warning' : ''}`}>
                  <div>
                    <strong>
                      {validLease
                        ? `관리자 ${user}가 일반 초안을 편집 중입니다.`
                        : '유효한 편집권이 없습니다. 입력은 유지됩니다.'}
                    </strong>
                    <p>
                      {dirty ? '미저장 변경 있음' : '저장된 초안'} · 최근 저장 {time(draft.savedAt)}{' '}
                      · 초안 저장은 공개본에 반영되지 않아요.
                    </p>
                  </div>
                  {validLease ? (
                    <Badge>
                      편집권 {Math.max(0, Math.ceil((c.lock.expiresAt - tick) / 1000))}초
                    </Badge>
                  ) : (
                    <div className="adm-actions">
                      <button onClick={copyInput}>입력 복사</button>
                      <button
                        onClick={() =>
                          ask(
                            '편집권과 최신 초안 확인',
                            '현재 입력을 먼저 복사하세요. 편집권 획득 후 최신 저장본을 열며 자동 병합하지 않습니다.',
                            () => {
                              const result = run(() => service.acquire(cityId, ctx()));
                              if (result) {
                                context.current.token = result.lock.token;
                                setDraft(result.draft);
                                setDirty(false);
                                setView('edit');
                                refresh();
                                setTick(Date.now());
                              }
                            },
                            false,
                            '최신본 열기',
                          )
                        }
                      >
                        편집권 다시 확인
                      </button>
                    </div>
                  )}
                </div>
                {conflict && view !== 'conflict' && (
                  <div className="adm-message error">
                    <span>안전정보가 갱신됐어요. 도시 전체 게시 전에 재검토가 필요합니다.</span>
                    <button
                      onClick={() => {
                        setReviewed(false);
                        setView('conflict');
                      }}
                    >
                      재검토 열기
                    </button>
                  </div>
                )}
                {view === 'edit' && (
                  <>
                    <div className="adm-tabs" role="tablist" aria-label="초안 항목">
                      {[
                        ['basic', '기본 정보'],
                        ['photos', '사진·권리'],
                        ['content', '경험·여행 정보'],
                        ['safety', '안전정보'],
                      ].map(([key, name]) => (
                        <button
                          key={key}
                          role="tab"
                          aria-selected={tab === key}
                          onClick={() => setTab(key)}
                        >
                          {name}
                        </button>
                      ))}
                    </div>
                    <section className="adm-card">
                      {tab === 'basic' && (
                        <>
                          <h2>도시 기본 정보</h2>
                          <div className="adm-grid two">
                            <Field label="한국어 도시명 · 최대 150자">
                              <input
                                maxLength={150}
                                value={draft.nameKo}
                                onChange={(e) => updateDraft('nameKo', e.target.value)}
                              />
                            </Field>
                            <Field label="영문 도시명 · 최대 150자">
                              <input
                                maxLength={150}
                                value={draft.nameEn}
                                onChange={(e) => updateDraft('nameEn', e.target.value)}
                              />
                            </Field>
                          </div>
                          <div className="adm-grid three">
                            <Field label="국가 · 생성 후 변경 불가">
                              <input value={c.country} readOnly />
                            </Field>
                            <Field label="위도">
                              <input
                                type="number"
                                min="-90"
                                max="90"
                                step="any"
                                value={draft.latitude}
                                onChange={(e) => updateDraft('latitude', e.target.value)}
                              />
                            </Field>
                            <Field label="경도">
                              <input
                                type="number"
                                min="-180"
                                max="180"
                                step="any"
                                value={draft.longitude}
                                onChange={(e) => updateDraft('longitude', e.target.value)}
                              />
                            </Field>
                          </div>
                          <Field label="한 줄 소개 · 최대 200자">
                            <textarea
                              maxLength={200}
                              value={draft.tagline}
                              onChange={(e) => updateDraft('tagline', e.target.value)}
                            />
                          </Field>
                          <fieldset className="adm-tag-options">
                            <legend>도시 태그 · 시연 사전</legend>
                            {['문화', '도보 여행', '자연', '음식'].map((tag) => (
                              <label key={tag}>
                                <input
                                  type="checkbox"
                                  checked={draft.tags.includes(tag)}
                                  onChange={(e) =>
                                    updateDraft(
                                      'tags',
                                      e.target.checked
                                        ? [...draft.tags, tag]
                                        : draft.tags.filter((x) => x !== tag),
                                    )
                                  }
                                />
                                {tag}
                              </label>
                            ))}
                          </fieldset>
                        </>
                      )}
                      {tab === 'photos' && (
                        <>
                          <div className="adm-section-heading">
                            <div>
                              <h2>사진과 이용 권리</h2>
                              <p className="adm-hint">
                                JPEG·PNG·WebP, 장당 10MiB / 4천만 픽셀 이하. 파일 미리보기는 현재
                                탭에서만 유지돼요.
                              </p>
                            </div>
                            <label className="adm-upload">
                              ＋ 사진 선택
                              <input
                                type="file"
                                accept="image/jpeg,image/png,image/webp"
                                onChange={addPhoto}
                              />
                            </label>
                          </div>
                          {draft.photos.map((p, i) => (
                            <article className="adm-photo-row" key={p.id}>
                              <div className="adm-photo-preview">
                                {uploads.current.get(p.id) ? (
                                  <img
                                    src={uploads.current.get(p.id)}
                                    alt={p.alt || '추가한 사진 미리보기'}
                                  />
                                ) : (
                                  <span>
                                    사진 {i + 1}
                                    <small>{p.name}</small>
                                  </span>
                                )}
                              </div>
                              <div className="adm-photo-fields">
                                <Field label={`사진 ${i + 1} 대체 텍스트`}>
                                  <input
                                    maxLength={500}
                                    value={p.alt}
                                    onChange={(e) =>
                                      updateDraft(
                                        'photos',
                                        draft.photos.map((x) =>
                                          x.id === p.id ? { ...x, alt: e.target.value } : x,
                                        ),
                                      )
                                    }
                                  />
                                </Field>
                                {[
                                  ['sourceUrl', '사진 출처 URL'],
                                  ['creator', '작가·제공자'],
                                  ['license', '라이선스 / 이용 근거'],
                                ].map(([key, label]) => (
                                  <Field key={key} label={label}>
                                    <input
                                      value={p[key]}
                                      onChange={(e) =>
                                        updateDraft(
                                          'photos',
                                          draft.photos.map((x) =>
                                            x.id === p.id
                                              ? { ...x, [key]: e.target.value, rights: false }
                                              : x,
                                          ),
                                        )
                                      }
                                    />
                                  </Field>
                                ))}
                                <label className="adm-check">
                                  <input
                                    type="checkbox"
                                    checked={p.rights}
                                    onChange={(e) =>
                                      updateDraft(
                                        'photos',
                                        draft.photos.map((x) =>
                                          x.id === p.id ? { ...x, rights: e.target.checked } : x,
                                        ),
                                      )
                                    }
                                  />
                                  이용 조건과 출처 표기 방법을 확인했습니다.
                                </label>
                                <div className="adm-actions">
                                  <button
                                    disabled={i === 0}
                                    onClick={() => reorder('photos', i, -1)}
                                  >
                                    위로
                                  </button>
                                  <button
                                    disabled={i === draft.photos.length - 1}
                                    onClick={() => reorder('photos', i, 1)}
                                  >
                                    아래로
                                  </button>
                                  <button
                                    className="adm-danger-text"
                                    onClick={() =>
                                      updateDraft(
                                        'photos',
                                        draft.photos.filter((x) => x.id !== p.id),
                                      )
                                    }
                                  >
                                    초안에서 제거
                                  </button>
                                </div>
                              </div>
                            </article>
                          ))}
                          {!draft.photos.length && (
                            <Empty title="사진을 추가해주세요">
                              게시하려면 권리를 확인한 사진과 대체 텍스트가 필요합니다.
                            </Empty>
                          )}
                        </>
                      )}
                      {tab === 'content' && (
                        <>
                          <div className="adm-section-heading">
                            <h2>대표 경험과 여행 정보</h2>
                            <button
                              onClick={() =>
                                updateDraft('contentItems', [
                                  ...draft.contentItems,
                                  {
                                    id: crypto.randomUUID(),
                                    kind: 'EXPERIENCE',
                                    title: '',
                                    body: '',
                                    selected: false,
                                    sourceUrl: '',
                                    evidence: '',
                                    confirmed: false,
                                    order: draft.contentItems.length + 1,
                                  },
                                ])
                              }
                            >
                              ＋ 항목 추가
                            </button>
                          </div>
                          <div className="adm-notice">
                            AI 제공자는 아직 연결하지 않았어요. 후보 자동 생성은 사용할 수 없으며,
                            지금은 직접 작성·검토하는 흐름입니다.
                          </div>
                          {draft.contentItems.map((item, i) => (
                            <article className="adm-content-card" key={item.id}>
                              <div className="adm-section-heading">
                                <Badge>
                                  {kindNames[item.kind]} · {i + 1}
                                </Badge>
                                <label className="adm-check">
                                  <input
                                    type="checkbox"
                                    checked={item.selected}
                                    onChange={(e) => patchContent(i, 'selected', e.target.checked)}
                                  />
                                  공개 대상 선정
                                </label>
                              </div>
                              <div className="adm-grid two">
                                <Field label="항목 종류">
                                  <select
                                    value={item.kind}
                                    onChange={(e) => patchContent(i, 'kind', e.target.value)}
                                  >
                                    {Object.entries(kindNames).map(([key, name]) => (
                                      <option key={key} value={key}>
                                        {name}
                                      </option>
                                    ))}
                                  </select>
                                </Field>
                                <Field label="제목 · 최대 200자">
                                  <input
                                    maxLength={200}
                                    value={item.title}
                                    onChange={(e) => patchContent(i, 'title', e.target.value)}
                                  />
                                </Field>
                              </div>
                              <Field label="본문 · 최대 3,000자">
                                <textarea
                                  maxLength={3000}
                                  value={item.body}
                                  onChange={(e) => patchContent(i, 'body', e.target.value)}
                                />
                              </Field>
                              <div className="adm-grid two">
                                <Field label="출처 URL">
                                  <input
                                    type="url"
                                    value={item.sourceUrl}
                                    onChange={(e) => patchContent(i, 'sourceUrl', e.target.value)}
                                  />
                                </Field>
                                <Field label="근거 메모 · 최대 4,000자">
                                  <input
                                    maxLength={4000}
                                    value={item.evidence}
                                    onChange={(e) => patchContent(i, 'evidence', e.target.value)}
                                  />
                                </Field>
                              </div>
                              <div className="adm-section-heading">
                                <label className="adm-check">
                                  <input
                                    type="checkbox"
                                    checked={item.confirmed}
                                    onChange={(e) =>
                                      updateDraft(
                                        'contentItems',
                                        draft.contentItems.map((x, j) =>
                                          i === j
                                            ? e.target.checked
                                              ? sourceCheck(x)
                                              : { ...x, confirmed: false }
                                            : x,
                                        ),
                                      )
                                    }
                                  />
                                  현재 내용과 근거를 확인했습니다.
                                </label>
                                <div className="adm-actions">
                                  <button
                                    disabled={i === 0}
                                    onClick={() => reorder('contentItems', i, -1)}
                                  >
                                    위로
                                  </button>
                                  <button
                                    disabled={i === draft.contentItems.length - 1}
                                    onClick={() => reorder('contentItems', i, 1)}
                                  >
                                    아래로
                                  </button>
                                  <button
                                    onClick={() =>
                                      updateDraft(
                                        'contentItems',
                                        draft.contentItems.filter((x) => x.id !== item.id),
                                      )
                                    }
                                  >
                                    제거
                                  </button>
                                </div>
                              </div>
                            </article>
                          ))}
                        </>
                      )}
                      {tab === 'safety' && (
                        <>
                          <h2>일반 초안에 포함할 안전정보</h2>
                          {!draft.safetyDraft ? (
                            <>
                              <div className="adm-notice">
                                안전정보를 수정하지 않으면 도시 게시 시 최신 공개 안전정보를
                                유지합니다.
                              </div>
                              <button
                                onClick={() => {
                                  if (draft.baseSafetyId !== c.safety.id)
                                    return notify(
                                      '안전정보가 바뀌었어요. 최신 공개본을 확인한 후 아래 재검토 시작을 사용해주세요.',
                                      true,
                                    );
                                  updateDraft('safetyDraft', copy(c.safety));
                                }}
                              >
                                이 초안에서 안전정보 수정 시작
                              </button>
                              {draft.baseSafetyId !== c.safety.id && (
                                <button
                                  onClick={() => {
                                    setDraft((d) => ({
                                      ...d,
                                      baseSafetyId: c.safety.id,
                                      safetyDraft: copy(c.safety),
                                    }));
                                    setDirty(true);
                                  }}
                                >
                                  최신 공개본을 확인하고 편집 시작
                                </button>
                              )}
                            </>
                          ) : (
                            <>
                              <p className="adm-hint">
                                수정을 시작한 안전정보만 제거하여 충돌을 우회할 수는 없어요. 초안
                                전체 폐기는 별도입니다.
                              </p>
                              <SafetyForm
                                value={draft.safetyDraft}
                                onChange={(v) => updateDraft('safetyDraft', v)}
                              />
                            </>
                          )}
                        </>
                      )}
                    </section>
                    <div className="adm-editor-footer">
                      <div>
                        <Badge>{dirty ? '미저장 변경 있음' : '저장됨'}</Badge>
                        <small>공개 상태: {labels[c.visibility]}</small>
                      </div>
                      <div className="adm-actions">
                        <button disabled={!validLease} onClick={save}>
                          초안 저장
                        </button>
                        <button
                          disabled={!validLease}
                          onClick={() => {
                            if (save()) {
                              release();
                              setView('cities');
                              refresh();
                            }
                          }}
                        >
                          저장하고 편집 종료
                        </button>
                        <button className="adm-primary" disabled={!validLease} onClick={preview}>
                          저장하고 미리보기
                        </button>
                      </div>
                    </div>
                    <details className="adm-management">
                      <summary>공개 상태·초안 폐기·도시 삭제</summary>
                      <div className="adm-actions">
                        <button
                          disabled={!validLease}
                          onClick={() =>
                            ask(
                              '비공개로 전환할까요?',
                              '도시 데이터와 이력은 유지됩니다. 미저장 입력은 공개본에 영향을 주지 않아요.',
                              () =>
                                run(() => {
                                  setState(service.visibility(cityId, 'HIDDEN', ctx()));
                                  notify('도시를 비공개로 전환했어요.');
                                }),
                            )
                          }
                        >
                          비공개 전환
                        </button>
                        <button
                          disabled={!validLease}
                          onClick={() =>
                            run(() => {
                              setState(service.visibility(cityId, 'PREPARING', ctx()));
                              notify('정보 준비 중으로 전환했어요.');
                            })
                          }
                        >
                          정보 준비 중
                        </button>
                        <button
                          disabled={!validLease}
                          onClick={() =>
                            ask(
                              '수정 초안을 폐기할까요?',
                              '현재 초안과 미저장 입력을 버립니다. 공개본과 현재 안전정보는 유지됩니다.',
                              () =>
                                run(() => {
                                  setState(service.discard(cityId, ctx()));
                                  context.current.token = null;
                                  setDirty(false);
                                  setView('cities');
                                }),
                              true,
                              '초안 폐기',
                            )
                          }
                        >
                          초안 폐기
                        </button>
                        <button
                          disabled={!validLease}
                          className="adm-danger-text"
                          onClick={() =>
                            ask(
                              '도시를 실제 삭제할까요?',
                              `${c.nameKo || '새 도시'}의 시연 업무 데이터를 제거하고 감사 이력만 남깁니다. 복원할 수 없어요.`,
                              () =>
                                run(() => {
                                  setState(service.remove(cityId, ctx()));
                                  context.current.token = null;
                                  setDirty(false);
                                  setView('cities');
                                  notify('도시를 삭제하고 감사 이력을 남겼어요.');
                                }),
                              true,
                              '도시 삭제',
                            )
                          }
                        >
                          도시 실제 삭제
                        </button>
                      </div>
                    </details>
                  </>
                )}
                {view === 'preview' && (
                  <>
                    <div className="adm-preview-grid">
                      <section className="adm-card">
                        <Badge>저장된 초안 미리보기</Badge>
                        <h2>
                          {draft.nameKo || '도시 이름 미입력'} <small>{draft.nameEn}</small>
                        </h2>
                        <p>{draft.tagline || '한 줄 소개 미입력'}</p>
                        <div className="adm-preview-photo">
                          {draft.photos.length
                            ? draft.photos[0].alt || '사진 대체 텍스트 미입력'
                            : '사진 없음'}
                        </div>
                        <div className="adm-tag-options">
                          {draft.tags.map((t) => (
                            <Badge key={t}>{t}</Badge>
                          ))}
                        </div>
                        <h3>대표 경험</h3>
                        {draft.contentItems
                          .filter((x) => x.kind === 'EXPERIENCE' && x.selected)
                          .slice(0, 3)
                          .map((x) => (
                            <article className="adm-preview-content" key={x.id}>
                              <h4>{x.title}</h4>
                              <p>{x.body}</p>
                            </article>
                          ))}
                        <Badge>
                          안전정보:{' '}
                          {(draft.safetyDraft || c.safety).reviewState === 'UNVERIFIED'
                            ? '미확인'
                            : '검토 정보 있음'}
                        </Badge>
                        <p className="adm-hint">실제 여행 정보가 아닌 시연 콘텐츠입니다.</p>
                      </section>
                      <section className="adm-card adm-publish-check">
                        <h2>게시 전 확인</h2>
                        <p className="adm-hint">
                          최근 저장 {time(draft.savedAt)} · 관리자 {draft.updatedBy}
                        </p>
                        {problems.length ? (
                          <ul className="adm-problems">
                            {problems.map((p, i) => (
                              <li key={i}>
                                <button
                                  onClick={() => {
                                    setView('edit');
                                    setTab(p.tab);
                                  }}
                                >
                                  {p.message} <Icon name="right" />
                                </button>
                              </li>
                            ))}
                          </ul>
                        ) : (
                          <div className="adm-notice">
                            필수 입력 검사를 통과했어요. 실제 자료의 사실·권리 검토까지 보장하지는
                            않습니다.
                          </div>
                        )}
                        {conflict && (
                          <div className="adm-message error">최신 안전정보 재검토가 필요해요.</div>
                        )}
                        <button
                          disabled={!!problems.length || conflict || !validLease}
                          className="adm-primary"
                          onClick={publish}
                        >
                          도시 전체 게시
                        </button>
                        <button onClick={() => setView('edit')}>편집으로 돌아가기</button>
                        {conflict && (
                          <button onClick={() => setView('conflict')}>안전정보 재검토</button>
                        )}
                      </section>
                    </div>
                  </>
                )}
                {view === 'conflict' && (
                  <section className="adm-card">
                    <h2>최신 정보와 내 수정 내용을 대조해주세요</h2>
                    <div className="adm-notice warning">
                      일반 콘텐츠까지 포함해 게시가 중단됩니다. 재검토 결과 저장은 게시가 아닙니다.
                    </div>
                    <div className="adm-grid two">
                      <div className="adm-compare">
                        <h3>최신 공개 안전정보</h3>
                        <p>
                          게시자 {c.safety.publishedBy || '—'} · {time(c.safety.publishedAt)}
                        </p>
                        <pre>{JSON.stringify(c.safety, null, 2)}</pre>
                      </div>
                      <div className="adm-compare">
                        <h3>내 수정본 · 기반이 달라졌어요</h3>
                        <SafetyForm
                          value={draft.safetyDraft || copy(c.safety)}
                          onChange={(v) => {
                            updateDraft('safetyDraft', v);
                            setReviewed(false);
                          }}
                        />
                      </div>
                    </div>
                    <label className="adm-check">
                      <input
                        type="checkbox"
                        checked={reviewed}
                        onChange={(e) => setReviewed(e.target.checked)}
                      />
                      최신 공개 정보와 내 수정 내용을 대조했습니다.
                    </label>
                    <div className="adm-actions">
                      <button
                        className="adm-primary"
                        disabled={
                          !reviewed || !validLease || !!safetyProblems(draft.safetyDraft).length
                        }
                        onClick={() =>
                          run(() => {
                            const saved = service.review(cityId, draft, c.safety.id, ctx());
                            setDraft(saved);
                            setDirty(false);
                            setView('preview');
                            refresh();
                            notify('재검토 결과를 초안에 저장했어요. 미리보기 후 게시해주세요.');
                          })
                        }
                      >
                        재검토 결과 저장 후 미리보기
                      </button>
                      <button onClick={() => setView('edit')}>편집으로 돌아가기</button>
                    </div>
                  </section>
                )}
              </>
            )}
            {view === 'safety' && c && (
              <section className="adm-card adm-safety-editor">
                <div className="adm-notice">
                  <strong>일반 초안의 잠금과 독립된 작업입니다.</strong>
                  <p>
                    게시해도 일반 초안·편집권·도시 공개 상태는 바뀌지 않아요. 미게시 입력은 현재
                    화면에만 유지됩니다.
                  </p>
                </div>
                {safetyBase !== c.safety.id && (
                  <div className="adm-message error">
                    <span>다른 안전정보가 게시됐어요. 아래 최신본과 입력을 다시 비교해주세요.</span>
                    <button
                      onClick={() => {
                        setSafetyBase(c.safety.id);
                        setReviewed(true);
                      }}
                    >
                      아래 최신본과 입력을 대조했어요
                    </button>
                  </div>
                )}
                {safetyBase !== c.safety.id && (
                  <pre className="adm-compare">{JSON.stringify(c.safety, null, 2)}</pre>
                )}
                <SafetyForm
                  value={safety}
                  onChange={(v) => {
                    setSafety(v);
                    setDirty(true);
                  }}
                />
                {safetyProblems(safety).length > 0 && (
                  <ul className="adm-inline-errors">
                    {safetyProblems(safety).map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                )}
                <div className="adm-actions">
                  <button
                    className="adm-primary"
                    disabled={safetyBase !== c.safety.id || !!safetyProblems(safety).length}
                    onClick={() =>
                      ask(
                        '안전정보만 게시할까요?',
                        '최신 버전을 다시 검사한 뒤 안전정보만 변경합니다.',
                        () =>
                          run(() => {
                            setState(service.publishSafety(cityId, safety, safetyBase, ctx()));
                            setDirty(false);
                            setView('cities');
                            notify('안전정보를 독립 게시했어요.');
                          }),
                        false,
                        '안전정보 게시',
                      )
                    }
                  >
                    안전정보 게시
                  </button>
                  <button onClick={() => navigate(() => setView('cities'))}>
                    목록으로 돌아가기
                  </button>
                </div>
              </section>
            )}
            {view === 'history' && (
              <>
                <div className="adm-history-search">
                  <Field label="도시명·도시 ID·작업 검색">
                    <input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="삭제된 도시도 이력에서 찾을 수 있어요"
                    />
                  </Field>
                </div>
                <div className="adm-history-grid">
                  <section className="adm-card adm-history-list">
                    <h2>최근 변경</h2>
                    {latestEvents.map((e) => (
                      <button
                        aria-pressed={selectedEvent?.id === e.id}
                        key={e.id}
                        onClick={() => setEventId(e.id)}
                      >
                        <strong>{actions[e.action] || e.action}</strong>
                        <span>
                          {e.cityName} · 관리자 {e.actor}
                        </span>
                        <small>{time(e.at)}</small>
                      </button>
                    ))}
                    {!latestEvents.length && (
                      <Empty title="이력이 없어요">검색어를 확인해주세요.</Empty>
                    )}
                  </section>
                  <section className="adm-card">
                    <h2>선택한 시점의 스냅샷</h2>
                    {selectedEvent ? (
                      <>
                        <p>
                          {selectedEvent.cityName} · {actions[selectedEvent.action]} · 관리자{' '}
                          {selectedEvent.actor}
                        </p>
                        <Badge>조회 전용 · 복원 기능 없음</Badge>
                        <div className="adm-grid two">
                          <div>
                            <h3>변경 전</h3>
                            <pre>{JSON.stringify(selectedEvent.before, null, 2)}</pre>
                          </div>
                          <div>
                            <h3>변경 후</h3>
                            <pre>{JSON.stringify(selectedEvent.after, null, 2)}</pre>
                          </div>
                        </div>
                      </>
                    ) : (
                      <p>이력을 선택해주세요.</p>
                    )}
                  </section>
                </div>
              </>
            )}
          </main>
          <footer className="adm-demo-tools">
            <strong>시연 도구</strong>
            {editView && (
              <>
                <button
                  disabled={!validLease}
                  onClick={() =>
                    run(() => {
                      setState(service.expire(cityId, ctx()));
                      setTick(Date.now());
                      notify('편집권 만료를 재현했어요. 입력은 유지됩니다.', true);
                    })
                  }
                >
                  편집권 만료 재현
                </button>
                <button
                  onClick={() =>
                    run(() => {
                      setState(service.simulateSafetyChange(cityId, ctx()));
                      notify('다른 관리자의 안전정보 게시를 재현했어요.');
                    })
                  }
                >
                  타인의 안전정보 게시 재현
                </button>
              </>
            )}
            <button
              onClick={() =>
                ask(
                  '시연 데이터 초기화',
                  '이 브라우저의 시연 도시·초안·이력을 처음 상태로 되돌립니다. 실제 서버 데이터는 없습니다.',
                  () => {
                    run(() => setState(service.reset()));
                    context.current.token = null;
                    setView('cities');
                    setDraft(null);
                    setDirty(false);
                  },
                  true,
                  '시연 초기화',
                )
              }
            >
              시연 초기화
            </button>
            <small>이 화면의 저장·게시·잠금은 브라우저 시뮬레이션입니다.</small>
          </footer>
        </>
      )}
      {modal && (
        <div className="adm-modal-backdrop">
          <section
            className="adm-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="adm-dialog-title"
            ref={dialog}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setModal(null);
              if (e.key === 'Tab') {
                const list = [...dialog.current.querySelectorAll('button,select,input')].filter(
                    (n) => !n.disabled,
                  ),
                  first = list[0],
                  last = list[list.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                  e.preventDefault();
                  last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                  e.preventDefault();
                  first.focus();
                }
              }
            }}
          >
            <h2 id="adm-dialog-title">{modal.title}</h2>
            <p>{modal.body}</p>
            {modal.title === '새 도시 추가' && (
              <Field label="국가">
                <select value={country} onChange={(e) => setCountry(e.target.value)}>
                  <option value="JP">일본</option>
                  <option value="IT">이탈리아</option>
                  <option value="CN">중국</option>
                </select>
              </Field>
            )}
            <div className="adm-actions">
              <button onClick={() => setModal(null)}>취소</button>
              <button
                className={modal.danger ? 'adm-danger' : 'adm-primary'}
                onClick={() => {
                  const action = modal.action;
                  setModal(null);
                  action(country);
                }}
              >
                {modal.confirmText}
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

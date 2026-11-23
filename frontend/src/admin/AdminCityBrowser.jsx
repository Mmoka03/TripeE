import React, { useEffect, useRef, useState } from 'react';
import { Badge, Empty, Field } from './AdminFields';

const visibilityNames = { PUBLIC: '공개', PREPARING: '준비 중', HIDDEN: '비공개' };
const cityName = (city) => city.nameKo || city.nameEn || '이름 미등록';

function CityDetail({ api, id, onBack, onUnauthorized }) {
  const [city, setCity] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const heading = useRef(null);
  useEffect(() => {
    const controller = new AbortController();
    setError('');
    setCity(null);
    api
      .city(id, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setCity(value);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        if (failure.status === 401) onUnauthorized(failure);
        else setError(failure.message);
      });
    return () => controller.abort();
  }, [api, id, attempt, onUnauthorized]);
  useEffect(() => {
    heading.current?.focus();
  }, []);

  return (
    <section className="adm-card adm-api-detail" aria-labelledby="city-heading">
      <button type="button" onClick={onBack}>
        ← 도시 목록으로
      </button>
      <h2 id="city-heading" ref={heading} tabIndex={-1}>
        {city ? cityName(city) : '도시 정보'}
      </h2>
      {error ? (
        <div role="alert">
          <p>{error}</p>
          <button onClick={() => setAttempt((value) => value + 1)}>다시 시도</button>
        </div>
      ) : !city ? (
        <p role="status">도시를 불러오는 중이에요.</p>
      ) : (
        <>
          <p className="adm-api-english">{city.nameEn || '영문명 미등록'}</p>
          <dl className="adm-api-facts">
            <div>
              <dt>국가</dt>
              <dd>
                {city.country.nameKo} · {city.country.nameEn}
              </dd>
            </div>
            <div>
              <dt>공개 상태</dt>
              <dd>
                <Badge tone={city.visibility === 'PUBLIC' ? 'green' : ''}>
                  {visibilityNames[city.visibility]}
                </Badge>
              </dd>
            </div>
            <div>
              <dt>게시본</dt>
              <dd>{city.publishedRevisionId ? '게시본 있음' : '아직 게시하지 않음'}</dd>
            </div>
            <div>
              <dt>수정 초안</dt>
              <dd>{city.draftRevisionId ? '초안 있음' : '초안 없음'}</dd>
            </div>
          </dl>
          {city.visibility === 'PUBLIC' && (
            <a
              className="adm-api-link"
              href={`/?city=${encodeURIComponent(city.id)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              공개 화면 보기 ↗
            </a>
          )}
          <p className="adm-hint">
            현재는 등록된 도시의 상태를 조회할 수 있어요. 콘텐츠 편집은 다음 단계에서 연결할
            예정입니다.
          </p>
        </>
      )}
    </section>
  );
}

function CityList({ api, query, onSelect, onUnauthorized }) {
  const [items, setItems] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [request, setRequest] = useState({ cursor: null, attempt: 0 });
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setError('');
    api
      .cities({ ...query, cursor: request.cursor, signal: controller.signal })
      .then((page) => {
        if (controller.signal.aborted) return;
        setItems((previous) =>
          request.cursor
            ? [...new Map([...previous, ...page.items].map((city) => [city.id, city])).values()]
            : page.items,
        );
        setNextCursor(page.nextCursor);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        if (failure.status === 401) onUnauthorized(failure);
        else setError(failure.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [api, query, request, onUnauthorized]);

  return (
    <section className="adm-card" aria-label="도시 목록" aria-busy={busy}>
      <div className="adm-api-list-heading">
        <h2>등록된 도시</h2>
        <span>불러온 도시 {items.length}개</span>
      </div>
      {items.length > 0 && (
        <ul className="adm-api-cities">
          {items.map((city) => (
            <li key={city.id}>
              <button id={`admin-city-${city.id}`} onClick={() => onSelect(city.id)}>
                <span className="adm-api-city-name">
                  <strong>{cityName(city)}</strong>
                  <span>
                    {city.nameEn || '영문명 미등록'} · {city.country.nameKo}
                  </span>
                </span>
                <Badge tone={city.visibility === 'PUBLIC' ? 'green' : ''}>
                  {visibilityNames[city.visibility]}
                </Badge>
                <span aria-hidden="true">→</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {busy && <p role="status">도시를 불러오는 중이에요.</p>}
      {error && (
        <div role="alert" className="adm-message error">
          <span>{error}</span>
          <button onClick={() => setRequest((value) => ({ ...value, attempt: value.attempt + 1 }))}>
            다시 시도
          </button>
        </div>
      )}
      {!busy && !error && items.length === 0 && (
        <Empty title="조회된 도시가 없어요">검색어와 공개 상태를 바꿔서 다시 확인해주세요.</Empty>
      )}
      {!busy && !error && nextCursor && (
        <button
          className="adm-api-more"
          onClick={() => setRequest({ cursor: nextCursor, attempt: 0 })}
        >
          도시 더 보기
        </button>
      )}
    </section>
  );
}

export default function AdminCityBrowser({ api, onUnauthorized }) {
  const [search, setSearch] = useState('');
  const [visibility, setVisibility] = useState('');
  const [query, setQuery] = useState({ q: '', visibility: '', revision: 0 });
  const [selectedId, setSelectedId] = useState(null);
  const [returnTo, setReturnTo] = useState(null);
  // 목록은 상세 조회 중에도 유지해 다음 페이지·검색 조건·스크롤을 잃지 않음.
  const [listScroll, setListScroll] = useState(0);
  function select(id) {
    setListScroll(window.scrollY);
    setSelectedId(id);
    setReturnTo(id);
    window.scrollTo(0, 0);
  }
  function back() {
    setSelectedId(null);
  }
  useEffect(() => {
    if (!selectedId && returnTo) {
      window.scrollTo(0, listScroll);
      document.getElementById(`admin-city-${returnTo}`)?.focus({ preventScroll: true });
    }
  }, [selectedId, returnTo, listScroll]);
  return (
    <>
      <div hidden={selectedId !== null}>
        <form
          className="adm-api-filters"
          role="search"
          onSubmit={(event) => {
            event.preventDefault();
            setQuery((value) => ({ q: search.trim(), visibility, revision: value.revision + 1 }));
            setReturnTo(null);
          }}
        >
          <Field label="도시 검색">
            <input
              type="search"
              maxLength={100}
              placeholder="도시명 또는 국가명"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </Field>
          <Field label="공개 상태">
            <select
              value={visibility}
              onChange={(event) => {
                setVisibility(event.target.value);
                setQuery((value) => ({
                  q: search.trim(),
                  visibility: event.target.value,
                  revision: value.revision + 1,
                }));
                setReturnTo(null);
              }}
            >
              <option value="">전체</option>
              <option value="PUBLIC">공개</option>
              <option value="PREPARING">준비 중</option>
              <option value="HIDDEN">비공개</option>
            </select>
          </Field>
          <button className="adm-primary" type="submit">
            검색
          </button>
        </form>
        <CityList
          key={JSON.stringify(query)}
          api={api}
          query={query}
          onSelect={select}
          onUnauthorized={onUnauthorized}
        />
      </div>
      {selectedId && (
        <CityDetail
          key={selectedId}
          api={api}
          id={selectedId}
          onBack={back}
          onUnauthorized={onUnauthorized}
        />
      )}
    </>
  );
}

import React, { useEffect, useMemo, useRef, useState } from 'react';
import MapView from './MapView';
import Icon from './Icon';
import { cities, countries, nearbyFixtures } from './data';
import './layout.css';

export default function App() {
  const [selected, setSelected] = useState(null),
    [query, setQuery] = useState(''),
    [searching, setSearching] = useState(false);
  const [group, setGroup] = useState([]),
    [size, setSize] = useState(34),
    [navigation, setNavigation] = useState(null);
  const [status, setStatus] = useState('loading'),
    [retry, setRetry] = useState(0),
    [mode, setMode] = useState('normal'),
    [testing, setTesting] = useState(true);
  const [photo, setPhoto] = useState(0);
  const closeButton = useRef(null),
    priorFocus = useRef(null),
    panel = useRef(null),
    touch = useRef(null),
    searchArea = useRef(null),
    searchInput = useRef(null),
    groupArea = useRef(null),
    groupTrigger = useRef(null);
  const shownCities = useMemo(() => (testing ? [...cities, ...nearbyFixtures] : cities), [testing]);
  const open = (city) => {
    priorFocus.current = document.activeElement;
    setSelected(city);
    setPhoto(0);
    setGroup([]);
    setSearching(false);
  };
  const close = () => {
    setSelected(null);
    const target = priorFocus.current?.isConnected ? priorFocus.current : searchInput.current;
    target?.focus?.();
    setSearching(false);
  };
  const closeGroup = () => {
    setGroup([]);
    const target = groupTrigger.current?.isConnected ? groupTrigger.current : searchInput.current;
    target?.focus?.();
    setSearching(false);
  };
  const openGroup = (items) => {
    groupTrigger.current = document.activeElement;
    setSelected(null);
    setSearching(false);
    setGroup(items);
  };
  useEffect(() => {
    if (selected) {
      closeButton.current?.focus();
      panel.current?.scrollTo(0, 0);
    }
  }, [selected]);
  useEffect(() => {
    if (group.length) groupArea.current?.querySelector('.cluster-option')?.focus();
  }, [group]);
  useEffect(() => {
    const dismiss = (e) => {
      if (!searchArea.current?.contains(e.target)) setSearching(false);
    };
    document.addEventListener('pointerdown', dismiss);
    document.addEventListener('focusin', dismiss);
    return () => {
      document.removeEventListener('pointerdown', dismiss);
      document.removeEventListener('focusin', dismiss);
    };
  }, []);
  useEffect(() => {
    const escape = (e) => {
      if (e.key !== 'Escape') return;
      if (searching && query.trim()) {
        searchInput.current?.focus();
        setSearching(false);
      } else if (group.length) closeGroup();
      else if (selected) close();
    };
    window.addEventListener('keydown', escape);
    return () => window.removeEventListener('keydown', escape);
  }, [searching, query, group, selected]);
  const term = query.trim().toLocaleLowerCase();
  const results = term
    ? [
        ...shownCities.map((c) => ({ ...c, type: 'city' })),
        ...countries.map((c) => ({ ...c, type: 'country' })),
      ].filter((c) =>
        [c.name, c.english, c.country, c.countryEn]
          .filter(Boolean)
          .some((s) => s.toLocaleLowerCase().includes(term)),
      )
    : [];
  const choose = (item) => {
    setSearching(false);
    setQuery('');
    setGroup([]);
    setNavigation({ ...item, serial: Date.now() });
    if (item.type === 'city') open(item);
    else setSelected(null);
  };
  return (
    <div className="app">
      <header>
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            close();
          }}
        >
          <span className="brand-mark">T</span>TripeE
          <span className="preview-badge">디자인 시안</span>
        </a>
        <span className="header-note">다음 여행, 도시에서 시작하세요.</span>
        <span className="sample-label">샘플 콘텐츠</span>
      </header>
      <main className={selected ? 'has-detail' : ''}>
        <section className="map-stage" aria-label="여행 도시 탐색">
          <MapView
            cities={shownCities}
            size={size}
            selectedId={selected?.id}
            navigation={navigation}
            retry={retry}
            onSelect={open}
            onGroup={openGroup}
            onStatus={setStatus}
          />
          <div
            className="map-top"
            ref={searchArea}
            onKeyDown={(e) => {
              if (!['ArrowDown', 'ArrowUp'].includes(e.key)) return;
              const buttons = [
                ...(searchArea.current?.querySelectorAll('.search-results button') || []),
              ];
              if (!buttons.length) return;
              e.preventDefault();
              const index = buttons.indexOf(document.activeElement);
              const next =
                index < 0
                  ? e.key === 'ArrowDown'
                    ? 0
                    : buttons.length - 1
                  : (index + (e.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
              buttons[next].focus();
            }}
          >
            <form
              role="search"
              onSubmit={(e) => {
                e.preventDefault();
                if (results.length === 1) choose(results[0]);
                else setSearching(true);
              }}
            >
              <Icon name="search" />
              <input
                ref={searchInput}
                aria-label="도시 또는 나라 검색"
                aria-controls="city-search-results"
                aria-expanded={Boolean(searching && term)}
                placeholder="도시 또는 나라를 검색하세요"
                value={query}
                onFocus={() => setSearching(true)}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setSearching(true);
                }}
              />
              <button type="submit" aria-label="검색">
                검색
              </button>
            </form>
            {searching && term && (
              <div id="city-search-results" className="search-results" aria-label="검색 결과">
                {results.length ? (
                  results.map((item) => (
                    <button
                      key={`${item.type}-${item.id || item.code}`}
                      onClick={() => choose(item)}
                    >
                      <span>
                        {item.name}
                        <small>{item.english}</small>
                      </span>
                      <small>{item.type === 'country' ? '나라로 이동' : item.country}</small>
                    </button>
                  ))
                ) : (
                  <p role="status">
                    검색 결과가 없어요.
                    <br />
                    <small>등록된 도시·나라명을 다시 확인해주세요.</small>
                  </p>
                )}
              </div>
            )}
          </div>
          <div className="map-intro">
            <span className="eyebrow">EXPLORE THE WORLD</span>
            <h1>
              어떤 도시가
              <br />
              궁금한가요?
            </h1>
            <p>
              지도 위 도시를 선택하고
              <br />
              다음 여행의 힌트를 찾아보세요.
            </p>
          </div>
          {status === 'loading' && (
            <div className="map-status" role="status">
              <span className="plane">
                <Icon name="plane" />
              </span>
              지도를 불러오는 중이에요.
            </div>
          )}
          {status === 'error' && (
            <div className="map-status error" role="alert">
              지도를 불러오지 못했어요.
              <button onClick={() => setRetry((v) => v + 1)}>다시 시도</button>
              <small>아래 도시 버튼으로 상세 배치는 확인할 수 있어요.</small>
            </div>
          )}
          <div className="map-bottom">
            <div className="city-shortcuts" aria-label="도시 바로 선택">
              {cities.map((c) => (
                <button key={c.id} aria-pressed={selected?.id === c.id} onClick={() => open(c)}>
                  {c.name}
                  <Icon name="arrow" />
                </button>
              ))}
            </div>
            <label className="pin-control">
              핀 크기
              <input
                type="range"
                min="28"
                max="48"
                value={size}
                onChange={(e) => setSize(Number(e.target.value))}
              />
            </label>
          </div>
          {group.length > 0 && (
            <section ref={groupArea} className="cluster-card" aria-label="겹친 도시 선택">
              <div className="section-heading">
                <h2>이 범위의 도시</h2>
                <button className="icon-button" onClick={closeGroup} aria-label="도시 목록 닫기">
                  <Icon name="close" />
                </button>
              </div>
              {group.map((c) => (
                <button className="cluster-option" key={c.id} onClick={() => open(c)}>
                  {c.name}
                  <span>
                    상세 보기 <Icon name="right" />
                  </span>
                </button>
              ))}
            </section>
          )}
        </section>
        {selected && (
          <aside className="detail" aria-label={`${selected.name} 도시 상세`} ref={panel}>
            <div className="detail-heading">
              <div>
                <span className="eyebrow">
                  {selected.country}
                  {selected.testOnly ? ' · 핀 겹침 검수용 도시' : ''}
                </span>
                <h2>
                  {selected.name}
                  <span>{selected.english}</span>
                </h2>
              </div>
              <button
                className="close icon-button"
                ref={closeButton}
                onClick={close}
                aria-label="도시 상세 닫기"
              >
                <Icon name="close" />
              </button>
            </div>
            {mode === 'loading' ? (
              <div className="state-view" role="status">
                <span className="plane">
                  <Icon name="plane" />
                </span>
                <h3>도시 정보를 불러오는 중이에요.</h3>
              </div>
            ) : mode === 'error' ? (
              <div className="state-view" role="alert">
                <h3>도시 정보를 불러오지 못했어요.</h3>
                <p>잠시 후 다시 시도해주세요.</p>
                <button onClick={() => setMode('normal')}>다시 시도</button>
              </div>
            ) : mode === 'preparing' ? (
              <div className="state-view">
                <h3>정보 준비 중</h3>
                <p>
                  도시 정보를 준비하고 있어요.
                  <br />
                  다른 도시도 둘러보세요.
                </p>
              </div>
            ) : (
              <>
                <p className="tagline">이 도시의 매력을 소개하는 한 줄이 들어갑니다.</p>
                <div
                  className={`photo photo-${photo}`}
                  role="group"
                  aria-label="사진 슬라이드 자리"
                  onTouchCancel={() => {
                    touch.current = null;
                  }}
                  onTouchStart={(e) => {
                    touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
                  }}
                  onTouchEnd={(e) => {
                    if (touch.current !== null) {
                      const delta = e.changedTouches[0].clientX - touch.current.x;
                      const vertical = e.changedTouches[0].clientY - touch.current.y;
                      if (Math.abs(delta) > 40 && Math.abs(delta) > Math.abs(vertical))
                        setPhoto((v) => (v + (delta < 0 ? 1 : 2)) % 3);
                      touch.current = null;
                    }
                  }}
                >
                  <span className="photo-caption">
                    사진 {photo + 1}
                    <small>권리 확인 후 도시 사진으로 교체</small>
                  </span>
                  <div className="photo-controls">
                    <button
                      className="icon-button"
                      aria-label="이전 사진"
                      onClick={() => setPhoto((v) => (v + 2) % 3)}
                    >
                      <Icon name="left" />
                    </button>
                    <span aria-live="polite">{photo + 1} / 3</span>
                    <button
                      className="icon-button"
                      aria-label="다음 사진"
                      onClick={() => setPhoto((v) => (v + 1) % 3)}
                    >
                      <Icon name="right" />
                    </button>
                  </div>
                </div>
                <div className="tags">
                  <span>태그 예시 A</span>
                  <span>태그 예시 B</span>
                  <span>태그 예시 C</span>
                </div>
                <a className="safety-summary" href="#safety">
                  <Icon name="alert" />
                  <span>
                    <strong>안전정보 미확인</strong>
                    <small>여행 전 공식 정보를 확인해주세요.</small>
                  </span>
                  <Icon name="arrow" />
                </a>
                <section className="experiences">
                  <div className="section-heading">
                    <h3>이 도시에서 할 수 있는 경험</h3>
                    <small>최대 3개</small>
                  </div>
                  {[1, 2, 3].map((i) => (
                    <article key={i}>
                      <span className="experience-number">0{i}</span>
                      <div>
                        <h4>대표 경험 제목 {i}</h4>
                        <p>
                          자료를 검토한 뒤 장소와 활동, 방문 시 고려할 점을 구체적으로 소개합니다.
                        </p>
                        <small>출처 확인 전 · 시안용 문구</small>
                      </div>
                    </article>
                  ))}
                </section>
                <section className="travel-info">
                  <h3>여행 전에 알아두세요</h3>
                  {['방문 시기', '도시 접근', '현지 이동', '고려사항'].map((title, i) => (
                    <details key={title} open={i === 0}>
                      <summary>{title}</summary>
                      <p>공식 자료 확보와 검토 후 제공할 정보입니다.</p>
                    </details>
                  ))}
                </section>
                <section id="safety" className="safety-detail">
                  <h3>안전정보와 출처</h3>
                  <p>
                    현재 이 시안에는 검토된 안전정보가 없습니다. 미확인은 안전함을 뜻하지 않습니다.
                  </p>
                  <a href="https://www.0404.go.kr/" target="_blank" rel="noreferrer">
                    외교부 해외안전여행 확인 ↗
                  </a>
                  <p className="muted">출처별 확인일과 적용 범위는 검토 후 표시합니다.</p>
                </section>
              </>
            )}
          </aside>
        )}
      </main>
      <div className="mobile-attribution" aria-label="지도 데이터 출처">
        <a href="https://openfreemap.org/" target="_blank" rel="noreferrer">
          OpenFreeMap
        </a>
        <a href="https://www.openmaptiles.org/" target="_blank" rel="noreferrer">
          © OpenMapTiles
        </a>
        <span>
          Data from{' '}
          <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">
            OpenStreetMap
          </a>
        </span>
      </div>
      <footer className="review-toolbar">
        <span>시안 검수</span>
        <label>
          상세 상태
          <select
            aria-label="상세 상태"
            value={mode}
            onChange={(e) => {
              setMode(e.target.value);
              if (!selected) open(cities[1]);
            }}
          >
            <option value="normal">기본 배치</option>
            <option value="loading">로딩</option>
            <option value="preparing">정보 준비 중</option>
            <option value="error">오류</option>
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            checked={testing}
            onChange={(e) => {
              setTesting(e.target.checked);
              setGroup([]);
              if (!e.target.checked && selected?.testOnly) close();
            }}
          />
          핀 겹침 테스트
        </label>
        <small>여행 정보·사진은 게시용 자료가 아닙니다.</small>
      </footer>
    </div>
  );
}

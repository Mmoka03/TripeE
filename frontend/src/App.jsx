import React, { useEffect, useMemo, useRef, useState } from 'react';
import MapView from './MapView';
import Icon from './Icon';
import ClusterCard from './ClusterCard';
import { mapThemes, getInitialTheme, themeVariables } from './mapThemes';
import { cities, countries, nearbyFixtures, worldCityFixtures } from './data';
import './layout.css';
import './travel-theme.css';

const RECENT_CITIES_KEY = 'tripee.recent-cities.v1';
const knownCities = [...cities, ...nearbyFixtures, ...worldCityFixtures];

function readRecentCities() {
  try {
    const saved = JSON.parse(localStorage.getItem(RECENT_CITIES_KEY) || '[]');
    return Array.isArray(saved)
      ? [...new Set(saved)].filter((id) => knownCities.some((city) => city.id === id)).slice(0, 3)
      : [];
  } catch {
    return [];
  }
}

function readExploration() {
  try {
    return JSON.parse(sessionStorage.getItem('tripee.exploration.v1') || '{}') || {};
  } catch {
    return {};
  }
}
const restored = readExploration();
const restoredGroup = Array.isArray(restored.group)
  ? restored.group.map((id) => knownCities.find((c) => c.id === id)).filter(Boolean)
  : [];
export default function App() {
  const [recentIds, setRecentIds] = useState(readRecentCities);
  const [selected, setSelected] = useState(
      knownCities.find((c) => c.id === restored.selected) || null,
    ),
    [query, setQuery] = useState(''),
    [searching, setSearching] = useState(false);
  const [group, setGroup] = useState(restoredGroup),
    [size, setSize] = useState(100),
    [navigation, setNavigation] = useState(null);
  const [status, setStatus] = useState('loading'),
    [retry, setRetry] = useState(0),
    [mode, setMode] = useState('normal'),
    [testing, setTesting] = useState(restored.testing !== false);
  const [themeId, setThemeId] = useState(getInitialTheme);
  const theme = mapThemes[themeId];
  const [photo, setPhoto] = useState(0);
  const [searchOpen, setSearchOpen] = useState(false);
  const searchToggle = useRef(null);
  const closeButton = useRef(null),
    priorFocus = useRef(null),
    panel = useRef(null),
    touch = useRef(null),
    searchArea = useRef(null),
    searchInput = useRef(null),
    groupArea = useRef(null),
    groupTrigger = useRef(null);
  const shownCities = useMemo(() => (testing ? knownCities : cities), [testing]);
  const recentCities = recentIds
    .map((id) => shownCities.find((city) => city.id === id))
    .filter(Boolean);
  const open = (city, fromGroup = false) => {
    priorFocus.current = document.activeElement;
    setSelected(city);
    setPhoto(0);
    if (!fromGroup) setGroup([]);
    setSearching(false);
    setSearchOpen(false);
    searchInput.current?.blur();
  };
  const close = () => {
    const cityId = selected?.id;
    setSelected(null);
    const target =
      priorFocus.current?.isConnected && !priorFocus.current.closest('[inert]')
        ? priorFocus.current
        : searchOpen
          ? searchInput.current
          : searchToggle.current;
    if (group.length)
      requestAnimationFrame(() => {
        const row = [...(groupArea.current?.querySelectorAll('.cluster-option') || [])].find(
          (button) => button.dataset.cityId === cityId,
        );
        (row || groupArea.current?.querySelector('.travel-card-close'))?.focus({
          preventScroll: true,
        });
      });
    else target?.focus?.({ preventScroll: true });
    setSearching(false);
  };
  const closeGroup = () => {
    setGroup([]);
    const target = groupTrigger.current?.isConnected
      ? groupTrigger.current
      : searchOpen
        ? searchInput.current
        : searchToggle.current;
    target?.focus?.({ preventScroll: true });
    setSearching(false);
  };
  const openGroup = (items) => {
    groupTrigger.current = document.activeElement;
    setSelected(null);
    setSearching(false);
    setSearchOpen(false);
    setGroup(items);
  };
  useEffect(() => {
    if (selected) {
      // 검색·지도 핀·직접 링크 모두 상세를 연 시점에 최근 기록을 갱신함.
      setRecentIds((previous) =>
        [selected.id, ...previous.filter((id) => id !== selected.id)].slice(0, 3),
      );
      closeButton.current?.focus();
      panel.current?.scrollTo(0, 0);
    }
  }, [selected]);
  useEffect(() => {
    try {
      localStorage.setItem(RECENT_CITIES_KEY, JSON.stringify(recentIds));
    } catch {
      // 저장이 제한된 브라우저에서도 현재 화면의 최근 기록은 사용할 수 있음.
    }
  }, [recentIds]);
  useEffect(() => {
    if (group.length)
      groupArea.current?.querySelector('.travel-card-close')?.focus({ preventScroll: true });
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
      } else if (selected) close();
      else if (group.length) closeGroup();
      else if (searchOpen) {
        setSearchOpen(false);
        searchToggle.current?.focus();
      }
    };
    window.addEventListener('keydown', escape);
    return () => window.removeEventListener('keydown', escape);
  }, [searching, query, group, selected, searchOpen]);
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('city');
    const city = cities.find((item) => item.id === id);
    if (city) {
      setSelected(city);
      setNavigation({ ...city, type: 'city', serial: Date.now() });
    }
  }, []);
  useEffect(() => {
    try {
      sessionStorage.setItem(
        'tripee.exploration.v1',
        JSON.stringify({ selected: selected?.id, group: group.map((c) => c.id), testing }),
      );
    } catch {
      /* 저장 제한 시 현재 화면만 유지함. */
    }
  }, [selected, group, testing]);
  const groupIds = useMemo(() => group.map((c) => c.id), [group]);
  const showGroup = (items) => {
    if (!items.length) return;
    const xs = items.map((c) => c.coordinates[0]),
      ys = items.map((c) => c.coordinates[1]);
    setNavigation({
      bounds: [
        [Math.min(...xs), Math.min(...ys)],
        [Math.max(...xs), Math.max(...ys)],
      ],
      serial: Date.now(),
      group: true,
    });
  };
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
    setSearchOpen(false);
    setQuery('');
    setGroup([]);
    setNavigation({ ...item, serial: Date.now() });
    if (item.type === 'city') open(item);
    else {
      setSelected(null);
      searchToggle.current?.focus({ preventScroll: true });
    }
  };
  return (
    <div className="app" style={themeVariables(theme)} data-theme={themeId}>
      <header>
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            close();
          }}
        >
          <img className="brand-icon" src="/brand/tripee-mark.svg" alt="" width="32" height="32" />
          TripeE
          <span className="preview-badge">디자인 시안</span>
        </a>
        <span className="header-note">다음 여행, 도시에서 시작하세요.</span>
        <span className="sample-label">샘플 콘텐츠</span>
        <a className="journey-link" href="/design/index.html">
          소개 보기 ↗
        </a>
      </header>
      <main className={selected ? 'has-detail' : group.length ? 'has-group' : ''}>
        <section className="map-stage" aria-label="여행 도시 탐색" data-map-status={status}>
          <h1 className="visually-hidden">여행 도시 지도</h1>
          <div
            className={`map-top ${searchOpen ? 'search-open' : ''}`}
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
            <div className="search-shell">
              <button
                className="search-toggle"
                ref={searchToggle}
                aria-label={searchOpen ? '검색창 닫기' : '도시 검색 열기'}
                aria-expanded={searchOpen}
                aria-controls="map-search-form"
                onClick={() => {
                  setSearchOpen(!searchOpen);
                  setSearching(false);
                  if (!searchOpen) requestAnimationFrame(() => searchInput.current?.focus());
                }}
              >
                <span className="search-glyph" aria-hidden="true">
                  <Icon name="search" />
                </span>
                <span className="close-glyph" aria-hidden="true">
                  <Icon name="close" />
                </span>
              </button>
              <form
                id="map-search-form"
                inert={!searchOpen}
                aria-hidden={!searchOpen}
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
                  placeholder="도시 또는 나라 검색"
                  value={query}
                  onFocus={() => setSearching(true)}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setSearching(true);
                  }}
                />
                <button type="submit" aria-label="검색">
                  <Icon name="search" />
                </button>
              </form>
            </div>
            {searchOpen && searching && term && (
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
          <MapView
            theme={theme}
            cities={shownCities}
            size={size}
            selectedId={selected?.id}
            groupIds={groupIds}
            navigation={navigation}
            retry={retry}
            onSelect={open}
            onGroup={openGroup}
            onStatus={setStatus}
          />
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
              <small>도시 검색으로 상세 배치를 확인할 수 있어요.</small>
            </div>
          )}
          <div className="map-attribution" aria-label="지도 데이터 출처">
            <a href="https://openmaptiles.org/" target="_blank" rel="noreferrer">
              © OpenMapTiles
            </a>
            <span aria-hidden="true"> · </span>
            <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">
              © OpenStreetMap
            </a>
            <span aria-hidden="true"> · </span>
            <a href="/design/index.html#credits">기타 출처</a>
          </div>
          <div className="map-bottom">
            {recentCities.length > 0 && (
              <div className="recent-city-group">
                <span className="recent-label">최근 본 도시</span>
                <div className="city-shortcuts" aria-label="최근 본 도시">
                  {recentCities.map((c) => (
                    <button
                      key={c.id}
                      title={c.name}
                      aria-pressed={selected?.id === c.id}
                      onClick={() => choose({ ...c, type: 'city' })}
                    >
                      <span>{c.name}</span>
                      <Icon name="arrow" />
                    </button>
                  ))}
                </div>
              </div>
            )}
            <label className="pin-control" htmlFor="pin-size">
              <span>핀 크기</span>
              <input
                id="pin-size"
                type="range"
                min="75"
                max="150"
                step="5"
                value={size}
                aria-valuetext={`${size}%`}
                onChange={(e) => setSize(Number(e.target.value))}
              />
              <output htmlFor="pin-size">{size}%</output>
            </label>
          </div>
          {group.length > 0 && (
            <ClusterCard
              key={group.map((city) => city.id).join('|')}
              ref={groupArea}
              cities={group}
              onClose={closeGroup}
              onSelect={(city) => open(city, true)}
              hidden={Boolean(selected)}
              onLocate={showGroup}
            />
          )}
        </section>
        {selected && (
          <aside className="detail" aria-label={`${selected.name} 도시 상세`} ref={panel}>
            <div className="detail-navigation">
              {group.length > 0 ? (
                <button
                  className="return-to-group"
                  aria-label="선택한 도시 목록으로 돌아가기"
                  onClick={close}
                >
                  <Icon name="left" /> 도시 목록 <span className="group-count">{group.length}</span>
                </button>
              ) : (
                <span className="detail-navigation-label">도시 정보</span>
              )}
              <button
                className="close icon-button"
                ref={closeButton}
                onClick={close}
                aria-label="도시 상세 닫기"
              >
                <Icon name="close" />
              </button>
            </div>
            <div className="detail-heading">
              <div>
                <span className="eyebrow">
                  {selected.country}
                  {selected.testOnly ? ' · 배치 검수용 도시' : ''}
                </span>
                <h2>
                  {selected.name}
                  <span>{selected.english}</span>
                </h2>
              </div>
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
      <footer className="review-toolbar">
        <span>시안 검수</span>
        <label className="theme-picker">
          지도 색상
          <select
            aria-label="지도 색상 시안"
            value={themeId}
            onChange={(e) => {
              const next = e.target.value;
              setThemeId(next);
              const url = new URL(window.location.href);
              url.searchParams.set('theme', next);
              window.history.replaceState(null, '', url);
            }}
          >
            {Object.entries(mapThemes).map(([key, value]) => (
              <option key={key} value={key}>
                {value.name}
              </option>
            ))}
          </select>
        </label>
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
          다수 도시 시연 ({knownCities.length}곳)
        </label>
        <small>여행 정보·사진은 게시용 자료가 아닙니다.</small>
      </footer>
    </div>
  );
}

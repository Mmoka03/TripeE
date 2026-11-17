// 지도와 UI가 같은 색상 정의를 공유함. 시안 변경 시 지도 인스턴스는 유지함.
export const mapThemes = {
  lagoon: {
    name: '라군',
    caption: '맑은 바다 · 산호색 포인트',
    sea: '#bdd9df',
    land: '#f9f6ed',
    boundary: '#a4b8b6',
    road: '#d0d7cd',
    paper: '#fcfaf5',
    ink: '#183c45',
    muted: '#526b70',
    accent: '#a43e33',
    dot: '#ffab88',
    pin: '#173c45',
    pinText: '#ffffff',
    line: '#b6c9c7',
    soft: '#e9eeea',
  },
  terra: {
    name: '테라코타',
    caption: '따뜻한 모래 · 벽돌색 포인트',
    sea: '#d6dce0',
    land: '#fff1dc',
    boundary: '#c6b69f',
    road: '#e2d1b6',
    paper: '#fff9ef',
    ink: '#56372e',
    muted: '#765c50',
    accent: '#a13f2c',
    dot: '#ffd29b',
    pin: '#763b2c',
    pinText: '#ffffff',
    line: '#c9bcae',
    soft: '#f0e5d7',
  },
  sage: {
    name: '세이지',
    caption: '차분한 녹색 · 머스터드 포인트',
    sea: '#c5d4c6',
    land: '#f7f7e9',
    boundary: '#a8b6a4',
    road: '#d7dcc6',
    paper: '#f7f8ee',
    ink: '#254b3e',
    muted: '#59705f',
    accent: '#805d21',
    dot: '#f4d070',
    pin: '#254b3e',
    pinText: '#ffffff',
    line: '#b3c4b1',
    soft: '#e6eddf',
  },
  midnight: {
    name: '미드나이트',
    caption: '짙은 남색 · 밝은 도시 핀',
    sea: '#1c2b40',
    land: '#405570',
    boundary: '#6d8195',
    road: '#4f6680',
    paper: '#182639',
    ink: '#f3eee3',
    muted: '#b8c7d6',
    accent: '#edab83',
    dot: '#9c442b',
    pin: '#f8dfa6',
    pinText: '#233044',
    line: '#586b81',
    soft: '#30445d',
  },
};

export function getInitialTheme() {
  const requested = new URLSearchParams(window.location.search).get('theme');
  return Object.hasOwn(mapThemes, requested) ? requested : 'lagoon';
}

export function themeVariables(theme) {
  return Object.fromEntries(
    Object.entries(theme)
      .filter(([key]) => !['name', 'caption'].includes(key))
      .map(([key, value]) => [`--map-${key}`, value]),
  );
}

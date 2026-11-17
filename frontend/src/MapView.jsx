import React, { useEffect, useRef } from 'react';
import { clusterCities } from './cityClusters';
import * as maplibregl from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
maplibregl.setWorkerUrl(workerUrl);

// 대륙명은 화면 표시용 위치이며 실제 경계 데이터가 아님.
const continents = [
  ['아시아', [90, 48]],
  ['유럽', [18, 54]],
  ['아프리카', [20, 3]],
  ['북아메리카', [-105, 46]],
  ['남아메리카', [-61, -18]],
  ['오세아니아', [139, -25]],
  ['남극', [15, -77]],
];

// 라벨 표시와 카메라는 유지하고 색상만 바꿈.
function paintTheme(map, theme) {
  for (const layer of map.getStyle().layers) {
    const source = layer['source-layer'] || '';
    if (layer.type === 'background') map.setPaintProperty(layer.id, 'background-color', theme.land);
    if (layer.type === 'fill')
      map.setPaintProperty(layer.id, 'fill-color', source === 'water' ? theme.sea : theme.land);
    if (layer.type === 'line')
      map.setPaintProperty(
        layer.id,
        'line-color',
        source === 'waterway' ? theme.sea : source === 'boundary' ? theme.boundary : theme.road,
      );
  }
}

function readCamera() {
  try {
    const value = JSON.parse(sessionStorage.getItem('tripee.camera.v1'));
    if (
      Array.isArray(value?.center) &&
      value.center.length === 2 &&
      value.center.every(Number.isFinite) &&
      Number.isFinite(value.zoom) &&
      value.zoom >= 0.5 &&
      value.zoom <= 12
    )
      return value;
  } catch {
    /* 저장된 카메라가 없으면 기본 위치를 사용함. */
  }
  return { center: [70, 33], zoom: window.innerWidth <= 700 ? 0.5 : 1.65 };
}

// 실제 패널 크기를 기준으로 검색한 도시가 보일 지도 영역을 계산함.
function navigationPadding(container, group) {
  const view = container.getBoundingClientRect();
  const panel = container.closest('main')?.querySelector(group ? '.travel-card' : '.detail');
  const padding = { top: 80, bottom: 60, left: 40, right: 60 };
  if (panel && panel.getBoundingClientRect().height > 0) {
    const box = panel.getBoundingClientRect();
    const overlaps = box.left < view.right && box.right > view.left && box.top < view.bottom;
    if (overlaps) {
      if (box.left > view.left + view.width * 0.35) padding.right = view.right - box.left + 24;
      else padding.bottom = view.bottom - box.top + 20;
    }
  }
  padding.top = Math.min(padding.top, Math.max(18, view.height - padding.bottom - 50));
  padding.left = Math.min(padding.left, Math.max(18, view.width - padding.right - 60));
  return padding;
}
export default function MapView({
  cities,
  theme,
  size,
  selectedId,
  groupIds,
  navigation,
  retry,
  onSelect,
  onGroup,
  onStatus,
}) {
  const container = useRef(null),
    map = useRef(null),
    markers = useRef([]),
    camera = useRef(readCamera());
  const themeRef = useRef(theme);
  themeRef.current = theme;
  const handlers = useRef({ onSelect, onGroup, onStatus });
  handlers.current = { onSelect, onGroup, onStatus };
  const appearance = useRef({ size, selectedId, groupIds });
  appearance.current = { size, selectedId, groupIds };
  const redraw = useRef(null);
  useEffect(() => {
    let m;
    const loadingTimeout = setTimeout(() => handlers.current.onStatus('error'), 20000);
    handlers.current.onStatus('loading');
    try {
      m = new maplibregl.Map({
        container: container.current,
        style:
          import.meta.env.VITE_MAP_STYLE_URL || 'https://tiles.openfreemap.org/styles/positron',
        ...camera.current,
        minZoom: 0.5,
        maxZoom: 12,
        renderWorldCopies: false,
        attributionControl: false,
      });
      map.current = m;
      m.on('moveend', () => {
        camera.current = { center: m.getCenter().toArray(), zoom: m.getZoom() };
        try {
          sessionStorage.setItem('tripee.camera.v1', JSON.stringify(camera.current));
        } catch {
          /* 저장 제한 시 복원을 생략함. */
        }
      });
      m.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
      m.dragRotate.disable();
      m.touchZoomRotate.disableRotation();
      m.on('style.load', () => {
        for (const layer of m.getStyle().layers) {
          if (layer.type === 'symbol') m.setLayoutProperty(layer.id, 'visibility', 'none');
        }
        paintTheme(m, themeRef.current);
      });
      m.on('load', () => {
        clearTimeout(loadingTimeout);
        for (const [name, coordinates] of continents) {
          const label = document.createElement('span');
          label.className = 'continent-label';
          label.textContent = name;
          label.setAttribute('aria-hidden', 'true');
          new maplibregl.Marker({ element: label }).setLngLat(coordinates).addTo(m);
        }
        handlers.current.onStatus('ready');
      });
      m.on('error', () => {
        clearTimeout(loadingTimeout);
        handlers.current.onStatus('error');
      });
    } catch {
      clearTimeout(loadingTimeout);
      handlers.current.onStatus('error');
    }
    const observer = new ResizeObserver(() => m?.resize());
    observer.observe(container.current);
    return () => {
      clearTimeout(loadingTimeout);
      observer.disconnect();
      if (m) camera.current = { center: m.getCenter().toArray(), zoom: m.getZoom() };
      markers.current.forEach((x) => x.remove());
      markers.current = [];
      m?.remove();
      map.current = null;
    };
  }, [retry]);
  useEffect(() => {
    const m = map.current;
    if (!m) return;
    const apply = () => paintTheme(m, theme);
    if (m.isStyleLoaded()) apply();
    else m.once('idle', apply);
    return () => m.off('idle', apply);
  }, [theme, retry]);
  useEffect(() => {
    const m = map.current;
    if (!m) return;
    let disposed = false;
    const markerById = new Map();
    const animations = new Set();
    const retiring = new Set();
    const finishMotion = () => {
      animations.forEach((animation) => animation.cancel());
      animations.clear();
      retiring.forEach((marker) => marker.remove());
      retiring.clear();
    };
    // 지도 위치는 MapLibre가 관리함. 내부 표식만 움직여 카메라 이동과 충돌하지 않게 함.
    const animate = (marker, from, to, leaving = false) => {
      const symbol = marker.getElement().querySelector('.pin-symbol');
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (reduced) {
        if (leaving) marker.remove();
        return;
      }
      const dx = from.x - to.x,
        dy = from.y - to.y;
      // 검색으로 멀리 이동한 경우 화면을 가로지르지 않고 짧게 나타나게 함.
      const local = Math.hypot(dx, dy) < 220;
      const offset = `translate(${local ? dx : 0}px, ${local ? dy : 0}px)`;
      const frames = leaving
        ? [
            { transform: 'translate(0, 0) scale(1)', opacity: 1 },
            {
              transform: `translate(${local ? -dx : 0}px, ${local ? -dy : 0}px) scale(.65)`,
              opacity: 0,
            },
          ]
        : [
            { transform: `${offset} scale(.65)`, opacity: 0 },
            { transform: 'translate(0, 0) scale(1)', opacity: 1 },
          ];
      const animation = symbol.animate(frames, {
        duration: leaving ? 260 : 420,
        easing: 'cubic-bezier(.22, 1, .36, 1)',
        fill: 'both',
      });
      animations.add(animation);
      animation.onfinish = () => {
        if (leaving) {
          marker.remove();
          retiring.delete(marker);
        }
        animation.cancel();
        animations.delete(animation);
      };
    };
    const draw = () => {
      finishMotion();
      const { size, selectedId, groupIds } = appearance.current;
      const scale = size / 100;
      const width = container.current.clientWidth;
      const height = container.current.clientHeight;
      // 가장 큰 표식의 폭과 여백을 반영해 확대된 핀끼리 겹치지 않도록 함.
      const radius = Math.max(
        window.matchMedia('(pointer: coarse)').matches ? 72 : 64,
        60 * scale + 8,
      );
      const groups = clusterCities(
        cities.map((city) => ({ city, ...m.project(city.coordinates) })),
        radius,
      );
      const visible = groups.filter(
        (group) =>
          group.x >= -30 && group.x <= width + 30 && group.y >= -30 && group.y <= height + 30,
      );
      const visibleIds = new Set(visible.map((group) => group.key));
      const previous = [...markerById].map(([key, marker]) => ({
        key,
        marker,
        cities: key.split('|').map((id) => ({ id })),
        ...m.project(marker.getLngLat()),
      }));
      // 공통 도시 수로 출발/도착 중심을 계산해 분리와 병합의 관계를 시각적으로 이어줌.
      const relatedCenter = (group, candidates) => {
        const ids = new Set(group.cities.map((city) => city.id));
        let x = 0,
          y = 0,
          count = 0;
        for (const candidate of candidates) {
          const weight = candidate.cities.filter((city) => ids.has(city.id)).length;
          x += candidate.x * weight;
          y += candidate.y * weight;
          count += weight;
        }
        return count ? { x: x / count, y: y / count } : group;
      };
      const focusedKey = [...markerById].find(
        ([, marker]) => marker.getElement() === document.activeElement,
      )?.[0];
      for (const [id, marker] of markerById) {
        if (!visibleIds.has(id)) {
          const old = previous.find((group) => group.key === id);
          const el = marker.getElement();
          el.inert = true;
          el.setAttribute('aria-hidden', 'true');
          el.style.pointerEvents = 'none';
          if (el === document.activeElement) el.blur();
          retiring.add(marker);
          animate(marker, old, relatedCenter(old, visible), true);
          markerById.delete(id);
        }
      }
      for (const group of visible) {
        const many = group.cities.length > 1;
        const city = group.cities[0];
        let marker = markerById.get(group.key);
        const entering = !marker;
        if (!marker) {
          const el = document.createElement('button');
          el.type = 'button';
          el.className = 'city-pin icon-city-pin' + (many ? ' cluster-pin' : '');
          el.dataset.cityId = many ? '' : city.id;
          el.dataset.count = String(group.cities.length);
          el.dataset.countries = String(group.countries);
          const symbol = document.createElement('span');
          symbol.className = 'pin-symbol';
          symbol.setAttribute('aria-hidden', 'true');
          if (many) symbol.textContent = String(group.cities.length);
          else {
            const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            svg.setAttribute('viewBox', '0 0 24 24');
            svg.setAttribute('fill', 'none');
            svg.setAttribute('stroke', 'currentColor');
            svg.setAttribute('stroke-width', '1.6');
            const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            path.setAttribute(
              'd',
              'M4 21V9h7v12m0-17h9v17M2 21h20M7 12v2m0 3v1m7-10h3m-3 4h3m-3 4h3',
            );
            svg.append(path);
            symbol.append(svg);
          }
          el.append(symbol);
          const tooltip = document.createElement('span');
          tooltip.className = 'pin-tooltip';
          tooltip.setAttribute('aria-hidden', 'true');
          tooltip.append(
            document.createElement('strong'),
            document.createElement('small'),
            document.createElement('em'),
          );
          el.append(tooltip);
          marker = new maplibregl.Marker({ element: el })
            .setLngLat(m.unproject([group.x, group.y]))
            .addTo(m);
          markerById.set(group.key, marker);
        }
        marker.setLngLat(m.unproject([group.x, group.y]));
        const el = marker.getElement();
        if (entering && previous.length) animate(marker, relatedCenter(group, previous), group);
        el.classList.toggle(
          'selected',
          group.cities.some((item) => item.id === selectedId || groupIds.includes(item.id)),
        );
        const symbolWidth = many ? Math.min(60, 46 + Math.log2(group.cities.length) * 2) : 28;
        el.style.setProperty('--marker-width', symbolWidth * scale + 'px');
        el.style.setProperty('--marker-height', (many ? 30 : 28) * scale + 'px');
        el.setAttribute(
          'aria-label',
          many
            ? group.cities.length + '개 도시, ' + group.countries + '개국. 도시 목록 열기'
            : city.name + ', ' + city.country + ' 상세 보기',
        );
        el.onclick = (event) => {
          event.stopPropagation();
          if (many) {
            const ordered = [...group.cities].sort((a, b) => {
              const pa = m.project(a.coordinates),
                pb = m.project(b.coordinates);
              return (
                Math.hypot(pa.x - group.x, pa.y - group.y) -
                Math.hypot(pb.x - group.x, pb.y - group.y)
              );
            });
            handlers.current.onGroup(ordered);
          } else handlers.current.onSelect(city);
        };
        el.dataset.below = String(group.y < 85);
        const tooltip = el.querySelector('.pin-tooltip');
        tooltip.querySelector('strong').textContent = many
          ? group.cities.length + '개 도시 · ' + group.countries + '개국'
          : city.name;
        tooltip.querySelector('small').textContent = many
          ? '현재 화면에서 가까이 있는 도시들'
          : city.country;
        tooltip.querySelector('em').textContent = many
          ? '클릭하여 선택 · 확대하여 나누기'
          : '도시 상세 보기';
        tooltip.style.maxWidth = Math.max(120, Math.min(220, width - 24)) + 'px';
        const tooltipWidth = tooltip.getBoundingClientRect().width;
        const left = Math.max(8, Math.min(width - tooltipWidth - 8, group.x - tooltipWidth / 2));
        tooltip.style.left = left - group.x + el.offsetWidth / 2 + 'px';
        tooltip.style.right = 'auto';
        tooltip.style.transform = 'translate(0, 5px)';
        // 키보드로 확대 중 묶음이 바뀌면 기존 도시가 포함된 새 핀으로 포커스를 이어줌.
        if (
          focusedKey &&
          !visibleIds.has(focusedKey) &&
          group.cities.some((item) => focusedKey.split('|').includes(item.id)) &&
          !container.current.contains(document.activeElement)
        )
          el.focus({ preventScroll: true });
      }
      markers.current = [...markerById.values()];
    };
    redraw.current = draw;
    draw();
    m.on('moveend', draw);
    m.on('movestart', finishMotion);
    m.on('resize', draw);
    document.fonts.ready.then(() => {
      if (!disposed) draw();
    });
    return () => {
      disposed = true;
      if (redraw.current === draw) redraw.current = null;
      m.off('moveend', draw);
      m.off('movestart', finishMotion);
      m.off('resize', draw);
      finishMotion();
      markerById.forEach((marker) => marker.remove());
      markers.current = [];
    };
  }, [cities, retry]);
  // 크기와 선택 상태만 바뀌면 기존 표식을 재사용함.
  useEffect(() => {
    redraw.current?.();
  }, [size, selectedId, groupIds]);
  useEffect(() => {
    const m = map.current;
    if (!m || !navigation) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const frame = requestAnimationFrame(() => {
      // 상세 패널로 지도 폭이 달라진 뒤의 크기를 사용함.
      m.resize();
      const padding = navigationPadding(container.current, navigation.group);
      if (navigation.bounds)
        m.fitBounds(navigation.bounds, {
          padding,
          maxZoom: 8,
          duration: reduced ? 0 : 800,
        });
      else
        m.easeTo({
          center: navigation.coordinates,
          zoom: 6,
          offset: [(padding.left - padding.right) / 2, (padding.top - padding.bottom) / 2],
          duration: reduced ? 0 : 800,
        });
    });
    return () => cancelAnimationFrame(frame);
  }, [navigation]);
  return (
    <div
      ref={container}
      className="map"
      style={{ '--pin-scale': size / 100 }}
      aria-label="도시 선택 세계지도"
    />
  );
}

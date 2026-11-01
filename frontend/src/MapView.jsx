import React, { useEffect, useRef } from 'react';
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

export default function MapView({
  cities,
  size,
  selectedId,
  navigation,
  retry,
  onSelect,
  onGroup,
  onStatus,
}) {
  const container = useRef(null),
    map = useRef(null),
    markers = useRef([]),
    camera = useRef({ center: [70, 33], zoom: 1.65 });
  const handlers = useRef({ onSelect, onGroup, onStatus });
  handlers.current = { onSelect, onGroup, onStatus };
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
      m.addControl(new maplibregl.AttributionControl({ compact: false }), 'bottom-left');
      m.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
      m.dragRotate.disable();
      m.touchZoomRotate.disableRotation();
      m.on('style.load', () => {
        // 기본 지명·도로·시설 라벨을 숨기고 대륙명과 도시 핀만 표시함.
        // 지도 제공처의 출처 표시는 유지함.
        for (const layer of m.getStyle().layers) {
          if (layer.type === 'symbol') m.setLayoutProperty(layer.id, 'visibility', 'none');
        }
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
    const draw = () => {
      markers.current.forEach((x) => x.remove());
      markers.current = [];
      const groups = [];
      for (const city of cities) {
        const point = m.project(city.coordinates);
        // 화면상의 픽셀 거리로 병합하며 확대 시 인접 도시가 분리되도록 함.
        const mergeRadius = Math.max(24, size * 0.75);
        const group = groups.find(
          (g) => Math.hypot(g.point.x - point.x, g.point.y - point.y) < mergeRadius,
        );
        if (group) group.cities.push(city);
        else groups.push({ point, cities: [city] });
      }
      for (const group of groups) {
        const first = group.cities[0],
          many = group.cities.length > 1;
        const el = document.createElement('button');
        el.type = 'button';
        el.className = `city-pin ${group.cities.some((c) => c.id === selectedId) ? 'selected' : ''}`;
        el.style.setProperty('--pin-size', `${size}px`);
        el.textContent = many ? `${group.cities.length}개 도시` : first.name;
        el.setAttribute(
          'aria-label',
          many ? `겹친 도시 ${group.cities.length}개 선택` : `${first.name} 상세 보기`,
        );
        el.onclick = (e) => {
          e.stopPropagation();
          many ? handlers.current.onGroup(group.cities) : handlers.current.onSelect(first);
        };
        markers.current.push(
          new maplibregl.Marker({ element: el }).setLngLat(first.coordinates).addTo(m),
        );
      }
    };
    draw();
    m.on('moveend', draw);
    m.on('resize', draw);
    return () => {
      m.off('moveend', draw);
      m.off('resize', draw);
    };
  }, [cities, size, selectedId, retry]);
  useEffect(() => {
    const m = map.current;
    if (!m || !navigation) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (navigation.bounds)
      m.fitBounds(navigation.bounds, { padding: 65, duration: reduced ? 0 : 800 });
    else m.easeTo({ center: navigation.coordinates, zoom: 6, duration: reduced ? 0 : 800 });
  }, [navigation]);
  return <div ref={container} className="map" aria-label="도시 선택 세계지도" />;
}

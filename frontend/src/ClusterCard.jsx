import React, { forwardRef, useRef, useState } from 'react';
import Icon from './Icon';

// 검수된 도시 사진이 없는 묶음에는 도시와 무관한 공통 이미지를 명시해 사용함.
const ClusterCard = forwardRef(function ClusterCard(
  { cities, onClose, onSelect, hidden, onLocate },
  ref,
) {
  const [query, setQuery] = useState('');
  const [country, setCountry] = useState('');
  const list = useRef(null);
  const resetListScroll = () => {
    list.current?.scrollTo(0, 0);
  };
  const filtered = cities.filter(
    (c) =>
      (!country || c.country === country) &&
      [c.name, c.english].some((v) => v?.toLowerCase().includes(query.trim().toLowerCase())),
  );
  const kyoto = cities.some((city) => city.code === 'JP' && city.english === 'Kyoto');
  const [failed, setFailed] = useState(false);
  return (
    <section
      ref={ref}
      className="cluster-card travel-card"
      aria-labelledby="cluster-title"
      style={hidden ? { display: 'none' } : undefined}
    >
      <div className="travel-card-cover">
        {!failed && (
          <img
            src={kyoto ? '/design/kyoto.jpg' : '/design/earth-night-2016.jpg'}
            alt={
              kyoto
                ? '교토 아라시야마의 도게츠교와 강 풍경'
                : '밤의 지구 — 도시 사진이 아닌 공통 탐색 이미지'
            }
            onError={() => setFailed(true)}
          />
        )}
        <div className="travel-card-heading">
          <span>YOUR NEXT JOURNEY</span>
          <h2 id="cluster-title">이곳에서 시작할 여행</h2>
          <p>
            {cities.length}개 도시 <span>·</span> {new Set(cities.map((city) => city.country)).size}
            개국
          </p>
        </div>
        <button
          className="icon-button travel-card-close"
          onClick={onClose}
          aria-label="도시 목록 닫기"
        >
          <Icon name="close" />
        </button>
      </div>
      <div className="travel-card-credit">
        {failed ? (
          '이미지를 불러오지 못했어요.'
        ) : (
          <>
            <span>{kyoto ? '교토 도게츠교' : '공통 탐색 이미지'}</span>
            <a href={`/design/index.html#credit-${kyoto ? 'kyoto' : 'earth'}`}>
              사진 출처·이용 조건
            </a>
          </>
        )}
      </div>
      <div className="group-tools">
        <div className="group-toolbar">
          <p>
            도시 선택 <span>{filtered.length}곳</span>
          </p>
          <button
            className="group-locate"
            onClick={() => onLocate(filtered)}
            disabled={!filtered.length}
            aria-label={`목록의 도시 ${filtered.length}곳을 지도에서 보기`}
          >
            <Icon name="map" /> 지도에 모아 보기
          </button>
        </div>
        {cities.length > 8 && (
          <div className="group-filters">
            <input
              aria-label="목록에서 도시 검색"
              placeholder="목록에서 도시 검색"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                resetListScroll();
              }}
            />
            <select
              aria-label="목록 국가 필터"
              value={country}
              onChange={(e) => {
                setCountry(e.target.value);
                resetListScroll();
              }}
            >
              <option value="">모든 국가</option>
              {[...new Set(cities.map((c) => c.country))].sort().map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
        )}
      </div>
      <div className="travel-card-list" ref={list} tabIndex="0" aria-label="묶음 도시 목록">
        {!filtered.length && (
          <p role="status">조건에 맞는 도시가 없어요. 검색어나 국가를 바꿔주세요.</p>
        )}
        {filtered.map((city, index) => (
          <button
            className="cluster-option"
            data-city-id={city.id}
            key={city.id}
            onClick={() => onSelect(city)}
          >
            <span className="travel-city-index" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <span className="nearby-city-name">
              {city.name}
              <small>
                {city.country} · {city.english}
              </small>
            </span>
            <Icon name="arrow" />
          </button>
        ))}
      </div>
      <div className="travel-card-footnote">묶음 중심에서 가까운 순서 · 샘플 콘텐츠</div>
    </section>
  );
});

export default ClusterCard;

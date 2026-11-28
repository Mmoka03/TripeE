import React, { useEffect, useEffectEvent, useRef, useState } from 'react';
import Icon from './Icon';
import { getJson, safeLink } from './publicApi';
import './public-api.css';

function Sources({ items = [] }) {
  return (
    <ul className="content-sources">
      {items.map((source) => (
        <li key={source.sourceDocumentId}>
          <a href={safeLink(source.url)} target="_blank" rel="noreferrer">
            {source.title} ↗
          </a>
          {source.checkedAt && (
            <span> · 등록 검토 {new Date(source.checkedAt).toLocaleDateString('ko-KR')}</span>
          )}
        </li>
      ))}
    </ul>
  );
}

function PhotoGallery({ photos }) {
  const [index, setIndex] = useState(0);
  const [failed, setFailed] = useState(false);
  const touch = useRef(null);
  const photo = photos[index];
  const move = (direction) => {
    setIndex((value) => (value + direction + photos.length) % photos.length);
    setFailed(false);
  };
  if (!photo) return <p className="muted">사진 정보가 미확인이에요.</p>;
  return (
    <figure className="city-photo">
      <div
        className="city-photo-frame"
        onTouchStart={(e) => {
          touch.current = [e.touches[0].clientX, e.touches[0].clientY];
        }}
        onTouchCancel={() => {
          touch.current = null;
        }}
        onTouchEnd={(e) => {
          if (!touch.current) return;
          const dx = e.changedTouches[0].clientX - touch.current[0],
            dy = e.changedTouches[0].clientY - touch.current[1];
          if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) && photos.length > 1)
            move(dx < 0 ? 1 : -1);
          touch.current = null;
        }}
      >
        {failed ? (
          <p role="status">
            사진을 불러오지 못했어요.<button onClick={() => setFailed(false)}>다시 시도</button>
          </p>
        ) : (
          <img src={photo.imageUrl} alt={photo.altText} onError={() => setFailed(true)} />
        )}
        {photos.length > 1 && (
          <div className="photo-controls">
            <button aria-label="이전 사진" onClick={() => move(-1)}>
              <Icon name="left" />
            </button>
            <span aria-live="polite">
              {index + 1} / {photos.length}
            </span>
            <button aria-label="다음 사진" onClick={() => move(1)}>
              <Icon name="right" />
            </button>
          </div>
        )}
      </div>
      <figcaption>
        {photo.caption && <p>{photo.caption}</p>}
        <a href={safeLink(photo.sourceUrl)} target="_blank" rel="noreferrer">
          사진 원본·출처
        </a>
        {' · '}
        {photo.creatorName || '저작자 미확인'}
        {' · '}
        <a href={safeLink(photo.licenseUrl)} target="_blank" rel="noreferrer">
          {photo.licenseName || '이용 조건'}
        </a>
      </figcaption>
    </figure>
  );
}

export default function CityDetail({ cityId, refresh, onAvailable, onUnavailable }) {
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const available = useEffectEvent(onAvailable);
  const unavailable = useEffectEvent(onUnavailable);
  useEffect(() => {
    const controller = new AbortController();
    getJson(`/cities/${encodeURIComponent(cityId)}`, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setResult({ cityId, data });
        available(cityId);
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error.status === 404) unavailable(cityId);
        else setResult({ cityId, error });
      });
    return () => controller.abort();
  }, [cityId, retry, refresh]);
  if (result?.cityId !== cityId)
    return (
      <div className="state-view" role="status">
        <span className="plane">
          <Icon name="plane" />
        </span>
        <h3>도시 정보를 불러오는 중이에요.</h3>
      </div>
    );
  if (result.error)
    return (
      <div className="state-view" role="alert">
        <h3>{result.error.message}</h3>
        <button onClick={() => setRetry((v) => v + 1)}>다시 시도</button>
      </div>
    );
  const city = result.data;
  if (city.visibility === 'PREPARING')
    return (
      <div className="state-view" role="status">
        <h3>정보 준비 중</h3>
        <p>{city.message}</p>
      </div>
    );
  return (
    <>
      <p className="tagline">{city.tagline}</p>
      <PhotoGallery key={city.revisionId} photos={city.photos} />
      <div className="tags">
        {city.tags.map((tag) => (
          <span key={tag.id} title={tag.labelEn}>
            {tag.labelKo}
          </span>
        ))}
      </div>
      <a className="safety-summary" href="#safety">
        <Icon name="alert" />
        <span>
          <strong>
            {
              {
                UNVERIFIED: '안전정보 미확인',
                PARTIAL: '안전정보 일부 확인',
                CHECKED: '안전정보 확인',
              }[city.safety.reviewState]
            }
          </strong>
          <small>여행 전 공식 정보를 확인해주세요.</small>
        </span>
        <Icon name="arrow" />
      </a>
      <section className="experiences">
        <div className="section-heading">
          <h3>이 도시에서 할 수 있는 경험</h3>
        </div>
        {city.experiences.map((item, index) => (
          <article key={item.id}>
            <span className="experience-number">{String(index + 1).padStart(2, '0')}</span>
            <div>
              <h4>{item.title}</h4>
              <p>{item.body}</p>
              <Sources items={item.sources} />
            </div>
          </article>
        ))}
      </section>
      <section className="travel-info">
        <h3>여행 전에 알아두세요</h3>
        {[
          ['visitPeriods', '방문 시기'],
          ['arrival', '도시 접근'],
          ['localTransport', '현지 이동'],
          ['considerations', '고려사항'],
        ].map(([key, title], index) => (
          <details key={key} open={index === 0}>
            <summary>{title}</summary>
            {city[key].length ? (
              city[key].map((item) => (
                <div key={item.id}>
                  <h4>{item.title}</h4>
                  <p>{item.body}</p>
                  <Sources items={item.sources} />
                </div>
              ))
            ) : (
              <p>해당 정보는 아직 미확인이에요.</p>
            )}
          </details>
        ))}
      </section>
      <section id="safety" className="safety-detail">
        <h3>안전정보와 출처</h3>
        {city.safety.reviewState === 'UNVERIFIED' && (
          <p>
            안전정보 미확인. 여행 전 공식 정보를 확인해주세요. 미확인은 안전함을 뜻하지 않습니다.
          </p>
        )}
        {city.safety.items.map((item) => (
          <article key={item.id}>
            <h4>{item.title}</h4>
            <p>{item.body}</p>
            <p className="muted">
              {item.originalScopeText} · {item.cityScopeNote}
            </p>
            <p>
              {
                {
                  EXPIRED: '적용 기간이 지난 정보',
                  UPCOMING: '적용 예정 정보',
                  CURRENT: '현재 적용 기간의 정보',
                }[item.temporalStatus]
              }
            </p>
            <Sources items={item.sources} />
          </article>
        ))}
        <a href="https://www.0404.go.kr/" target="_blank" rel="noreferrer">
          외교부 해외안전여행 확인 ↗
        </a>
      </section>
    </>
  );
}

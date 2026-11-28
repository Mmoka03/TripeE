export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function onPublicMapReturn(onReturn, page = document) {
  const view = page.defaultView;
  let timer;
  // 탭 복귀 때 연달아 오는 visibilitychange와 focus를 한 번의 조회로 묶음.
  const refresh = () => {
    clearTimeout(timer);
    if (page.visibilityState === 'visible') timer = setTimeout(onReturn, 50);
  };
  const restorePage = (event) => {
    if (event.persisted) refresh();
  };
  page.addEventListener('visibilitychange', refresh);
  view.addEventListener('focus', refresh);
  view.addEventListener('pageshow', restorePage);
  return () => {
    clearTimeout(timer);
    page.removeEventListener('visibilitychange', refresh);
    view.removeEventListener('focus', refresh);
    view.removeEventListener('pageshow', restorePage);
  };
}

export async function getJson(path, signal) {
  let response;
  try {
    response = await fetch(`/api/v1${path}`, {
      signal,
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    if (signal?.aborted || error.name === 'AbortError') throw error;
    throw new ApiError(503, '서버에 연결하지 못했어요. 잠시 후 다시 시도해주세요.');
  }
  if (!response.ok)
    throw new ApiError(
      response.status,
      response.status === 404
        ? '현재 공개된 도시 정보가 없어요.'
        : '정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.',
    );
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new ApiError(502, '정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
  try {
    return await response.json();
  } catch (error) {
    if (signal?.aborted || error.name === 'AbortError') throw error;
    throw new ApiError(502, '정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
  }
}

export function mapCity(city) {
  return {
    id: city.id,
    name: city.nameKo,
    english: city.nameEn,
    country: city.country.nameKo,
    countryEn: city.country.nameEn,
    code: city.country.code,
    visibility: city.visibility,
    coordinates: [city.coordinates.longitude, city.coordinates.latitude],
  };
}

export function mapSearchResult(result, cities, countries) {
  if (result.type === 'CITY') return { ...mapCity(result.city), type: 'city' };
  const country = result.country;
  const points = cities.filter((c) => c.code === country.code).map((c) => c.coordinates);
  const bounds =
    countries.find((c) => c.code === country.code)?.bounds ||
    (points.length
      ? [
          [Math.min(...points.map((p) => p[0])), Math.min(...points.map((p) => p[1]))],
          [Math.max(...points.map((p) => p[0])), Math.max(...points.map((p) => p[1]))],
        ]
      : null);
  return {
    type: 'country',
    code: country.code,
    name: country.nameKo,
    english: country.nameEn,
    bounds,
  };
}

export function safeLink(value) {
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}

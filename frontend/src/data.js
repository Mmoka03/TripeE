import worldCityData from './worldCityFixtures.json';

// 공개 좌표를 활용한 밀집도 검수용 데이터임. 여행 정보가 게시된 도시 목록이 아님.
// Natural Earth populated places 110m / public domain
// https://www.naturalearthdata.com/downloads/110m-cultural-vectors/110m-populated-places/
const regionNames = new Intl.DisplayNames(['ko'], { type: 'region' });
export const worldCityFixtures = worldCityData.map((city) => ({
  ...city,
  country: /^[A-Z]{2}$/.test(city.code) ? regionNames.of(city.code) : city.countryEn,
}));

// 화면 검수용 데이터임. 실제 여행 콘텐츠와 사진은 자료 검수 후 연결할 예정임.
export const cities = [
  {
    id: '1',
    name: '나폴리',
    english: 'Naples',
    country: '이탈리아',
    countryEn: 'Italy',
    code: 'IT',
    coordinates: [14.2681, 40.8518],
  },
  {
    id: '2',
    name: '교토',
    english: 'Kyoto',
    country: '일본',
    countryEn: 'Japan',
    code: 'JP',
    coordinates: [135.7681, 35.0116],
  },
  {
    id: '3',
    name: '베이징',
    english: 'Beijing',
    country: '중국',
    countryEn: 'China',
    code: 'CN',
    coordinates: [116.4074, 39.9042],
  },
];
// 국가 검색 시 이동할 임시 범위임. 등록 도시들의 좌표 범위와는 별개임.
export const countries = [
  {
    code: 'IT',
    name: '이탈리아',
    english: 'Italy',
    bounds: [
      [6.62, 35.48],
      [18.52, 47.1],
    ],
  },
  {
    code: 'JP',
    name: '일본',
    english: 'Japan',
    bounds: [
      [122.9, 20.4],
      [153.99, 45.6],
    ],
  },
  {
    code: 'CN',
    name: '중국',
    english: 'China',
    bounds: [
      [73.5, 18.1],
      [135.1, 53.6],
    ],
  },
];
// 핀 겹침 확인용 인접 도시임. 초기 공개 대상에 포함하지 않음.
// 좌표 출처: GeoNames(CC BY)를 사용함.
// https://www.geonames.org/1853909/osaka.html
// https://www.geonames.org/1853574/otsu.html
export const nearbyFixtures = [
  {
    id: 'test-osaka',
    name: '오사카',
    english: 'Osaka',
    country: '일본',
    countryEn: 'Japan',
    code: 'JP',
    coordinates: [135.50107, 34.69379],
    testOnly: true,
  },
  {
    id: 'test-otsu',
    name: '오쓰',
    english: 'Otsu',
    country: '일본',
    countryEn: 'Japan',
    code: 'JP',
    coordinates: [135.86667, 35.0],
    testOnly: true,
  },
];

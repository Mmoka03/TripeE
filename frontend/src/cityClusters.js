// 화면 거리로 가까운 묶음부터 병합함. 국경은 묶음의 기준으로 사용하지 않음.
// 원본 도시를 매번 다시 투영해 확대 시 개별 핀으로 되돌아갈 수 있게 함.
export function clusterCities(points, radius) {
  const groups = [...points]
    .sort((a, b) => a.city.id.localeCompare(b.city.id))
    .map(({ city, x, y }) => ({ cities: [city], x, y }));
  const limit = radius * radius;
  while (groups.length > 1) {
    let closest = limit;
    let pair;
    for (let i = 0; i < groups.length; i++) {
      for (let j = i + 1; j < groups.length; j++) {
        const distance = (groups[i].x - groups[j].x) ** 2 + (groups[i].y - groups[j].y) ** 2;
        if (distance < closest) {
          closest = distance;
          pair = [i, j];
        }
      }
    }
    if (!pair) break;
    const [i, j] = pair;
    const a = groups[i],
      b = groups[j];
    const count = a.cities.length + b.cities.length;
    groups[i] = {
      cities: [...a.cities, ...b.cities],
      x: (a.x * a.cities.length + b.x * b.cities.length) / count,
      y: (a.y * a.cities.length + b.y * b.cities.length) / count,
    };
    groups.splice(j, 1);
  }
  return groups.map((group) => ({
    ...group,
    key: group.cities
      .map((city) => city.id)
      .sort()
      .join('|'),
    countries: new Set(group.cities.map((city) => city.country)).size,
  }));
}

(() => {
  const { cities, storageKey, selectExamples } = window.TripeEIntro;
  let previous;
  try {
    previous = JSON.parse(sessionStorage.getItem(storageKey));
  } catch {
    // 저장소 사용이 제한돼도 소개 페이지는 표시함. 직전 배치 제외만 보장하지 못함.
  }
  const examples = selectExamples(cities, previous);
  if (!examples) return;
  try {
    sessionStorage.setItem(
      storageKey,
      JSON.stringify(
        Object.fromEntries(Object.entries(examples).map(([slot, city]) => [slot, city.id])),
      ),
    );
  } catch {
    /* 저장소 사용이 제한된 환경에서도 표시를 계속함. */
  }

  const $ = (selector) => document.querySelector(selector);
  const cityLink = (city) => `/?city=${encodeURIComponent(city.id)}`;
  const setPhoto = (selector, photo) => {
    const img = $(selector);
    img.src = photo.src;
    img.alt = photo.alt;
    img.width = photo.width;
    img.height = photo.height;
    img.style.objectPosition = photo.position;
  };
  const setCredit = (link, photo) => {
    link.href = `#${photo.creditId}`;
    link.textContent = `${photo.author} · ${photo.license}`;
  };
  const { hero, bridge, perspective } = examples;
  for (const [slot, city] of Object.entries(examples))
    document.body.dataset[`${slot}City`] = city.id;
  setPhoto('.hero-photo img', hero.photo);
  $('.hero-meta strong').textContent = `${hero.name}, ${hero.country}`;
  $('.hero-meta > div > span:last-child').textContent = `${hero.english}, ${hero.countryEnglish}`;
  const heroCredit = document.createElement('a');
  setCredit(heroCredit, hero.photo);
  $('.hero-bottom .photo-attribution').replaceChildren(heroCredit);

  setPhoto('.fold-photograph img', bridge.photo);
  $('.fold-photo-title > span').textContent =
    `${bridge.countryEnglish.toUpperCase()} / ${bridge.native}`;
  $('.fold-photo-title h3').firstChild.textContent = bridge.name;
  $('.fold-photo-title h3 span').textContent = bridge.english;
  $('.fold-photo-title > a').href = cityLink(bridge);
  const [lng, lat] = bridge.coordinates;
  $('.fold-panel-destination').textContent =
    `${Math.abs(lat).toFixed(2)}° ${lat < 0 ? 'S' : 'N'} / ${Math.abs(lng).toFixed(2)}° ${lng < 0 ? 'W' : 'E'}`;
  setCredit($('.fold-caption > a'), bridge.photo);

  setPhoto('.journey-mood > img', perspective.mood);
  setPhoto('.journey-guide-photo img', perspective.photo);
  $('.journey-mood-copy p').textContent = `${perspective.english}, ${perspective.countryEnglish}`;
  setCredit($('.journey-deck-credits > a:first-of-type'), perspective.mood);
  setCredit($('.journey-deck-credits > a:last-of-type'), perspective.photo);
  $('.perspective-copy > a').href = cityLink(perspective);

  // 전체 도시로 가는 입구를 유지하고 티켓 순서도 예제 도시와 함께 바꿈.
  const tickets = [hero, bridge, perspective];
  tickets.forEach((city, index) => {
    const ticket = $(`.ticket-${city.key}`);
    ticket.querySelector('.ticket-index').textContent = String(index + 1).padStart(2, '0');
    $('.destination-tickets').append(ticket);
  });

  // 현재 보이지 않는 사진도 포함해 모든 후보의 출처를 푸터에 남김.
  for (const city of cities) {
    for (const photo of [city.photo, city.mood]) {
      if (document.getElementById(photo.creditId)) continue;
      const paragraph = document.createElement('p');
      paragraph.id = photo.creditId;
      paragraph.tabIndex = -1;
      const title = document.createElement('strong');
      title.textContent = `${city.name} 사진 · ${photo === city.photo ? '도시 풍경' : '세부 풍경'}`;
      const source = document.createElement('a');
      source.href = photo.source;
      source.textContent = photo.title;
      const license = document.createElement('a');
      license.href = photo.licenseUrl;
      license.textContent = photo.license;
      paragraph.append(
        title,
        document.createElement('br'),
        source,
        ` · ${photo.author} · `,
        license,
        document.createElement('br'),
        '도시 소개용 기록 사진입니다. 화면 비율에 맞게 크롭하고 일부 화면에 명암·색감 조정을 적용했습니다.',
      );
      $('.credit-links').insertBefore(paragraph, $('#credit-map'));
    }
  }
})();

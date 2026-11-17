// 사진·출처·좌표·이동 대상을 한 묶음으로 관리함. 지도 시연용 도시 전체를 후보로 쓰지 않음.
(() => {
  const commons = 'https://commons.wikimedia.org/wiki/File:';
  const cc0 = 'https://creativecommons.org/publicdomain/zero/1.0/';
  const cities = [
    {
      id: '1',
      key: 'naples',
      name: '나폴리',
      english: 'Naples',
      native: 'Napoli',
      country: '이탈리아',
      countryEnglish: 'Italy',
      coordinates: [14.2681, 40.8518],
      photo: {
        src: 'naples.jpg',
        width: 2832,
        height: 2128,
        alt: '나폴리 해안과 포실리포 언덕의 풍경',
        title: 'Napoli Posillipo',
        author: 'Francesco Russo',
        license: 'Public domain',
        source: commons + 'Napoli_Posillipo.jpg',
        licenseUrl: commons + 'Napoli_Posillipo.jpg#Licensing',
        creditId: 'credit-naples',
        position: '50% 52%',
      },
      mood: {
        src: 'naples-castle.jpg',
        width: 1280,
        height: 879,
        alt: '나폴리 해안에 자리한 카스텔 델로보와 푸른 바다',
        title: "Castel del'Ovo Naples",
        author: 'Jebulon',
        license: 'CC0 1.0',
        source: commons + 'Castel_del%27Ovo_Naples.jpg',
        licenseUrl: cc0,
        creditId: 'credit-naples-castle',
        position: '50% 45%',
      },
    },
    {
      id: '2',
      key: 'kyoto',
      name: '교토',
      english: 'Kyoto',
      native: '京都',
      country: '일본',
      countryEnglish: 'Japan',
      coordinates: [135.7681, 35.0116],
      photo: {
        src: 'kyoto.jpg',
        width: 2400,
        height: 1596,
        alt: '산과 벚꽃 아래 교토 아라시야마의 강을 가로지르는 도게츠교',
        title: 'Arashiyama Togetsukyo-bridge',
        author: 'Tomomarusan',
        license: 'CC BY 2.5',
        source: commons + 'Arashiyama_Togetsukyo-bridge.jpg',
        licenseUrl: 'https://creativecommons.org/licenses/by/2.5/',
        creditId: 'credit-kyoto',
        position: '50% 50%',
      },
      mood: {
        src: 'kyoto-bamboo.jpg',
        width: 1280,
        height: 853,
        alt: '교토의 대나무숲 사이로 이어지는 길',
        title: 'Bamboo forest Kyoto',
        author: 'lb0104672',
        license: 'CC0 1.0',
        source: commons + 'Bamboo_forest_Kyoto.jpg',
        licenseUrl: cc0,
        creditId: 'credit-bamboo',
        position: '50% 50%',
      },
    },
    {
      id: '3',
      key: 'beijing',
      name: '베이징',
      english: 'Beijing',
      native: '北京',
      country: '중국',
      countryEnglish: 'China',
      coordinates: [116.4074, 39.9042],
      photo: {
        src: 'beijing.jpg',
        width: 2736,
        height: 1824,
        alt: '베이징 천단 기년전의 푸른 지붕과 붉은 기둥',
        title: '2014.09.29.165903 Temple of Heaven Bejing',
        author: 'Hermann Luyken',
        license: 'CC0 1.0',
        source: commons + '2014.09.29.165903_Temple_of_Heaven_Bejing.jpg',
        licenseUrl: cc0,
        creditId: 'credit-beijing',
        position: '50% 48%',
      },
      mood: {
        src: 'beijing-palace.jpg',
        width: 2304,
        height: 1728,
        alt: '베이징 이화원 사대부주의 지붕과 동물 모양 장식',
        title: '2014.08.27.125220 Ornaments Sumeru Lingjing Summer Palace Beijing',
        author: 'Hermann Luyken',
        license: 'CC0 1.0',
        source: commons + '2014.08.27.125220_Ornaments_Sumeru_Lingjing_Summer_Palace_Beijing.jpg',
        licenseUrl: cc0,
        creditId: 'credit-beijing-palace',
        position: '50% 45%',
      },
    },
  ];

  const storageKey = 'tripee:intro-examples:v1';
  const slots = ['bridge', 'hero', 'perspective'];
  const isShortName = (city) =>
    /^[가-힣]{1,3}$/.test(city.name) && /^[A-Za-z]{1,8}$/.test(city.english);
  const shuffle = (items, random) => {
    const result = [...items];
    for (let i = result.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1));
      [result[i], result[j]] = [result[j], result[i]];
    }
    return result;
  };

  function selectExamples(pool, previous = {}, random = Math.random) {
    // 단순 무작위 추첨은 같은 도시가 연속될 수 있어, 직전 배치를 제외하고 조합을 찾음.
    function pick(index, chosen, avoidPrevious) {
      if (index === slots.length) return chosen;
      const slot = slots[index];
      const candidates = pool.filter(
        (city) =>
          (slot !== 'bridge' || isShortName(city)) &&
          !Object.values(chosen).some((entry) => entry.id === city.id) &&
          (!avoidPrevious || previous?.[slot] !== city.id),
      );
      for (const city of shuffle(candidates, random)) {
        const result = pick(index + 1, { ...chosen, [slot]: city }, avoidPrevious);
        if (result) return result;
      }
      return null;
    }
    // 후보가 줄어든 경우에는 연속 표시보다 서로 다른 도시·짧은 이름 기준을 우선함.
    return pick(0, {}, true) || pick(0, {}, false);
  }

  globalThis.TripeEIntro = { cities, storageKey, isShortName, selectExamples };
})();

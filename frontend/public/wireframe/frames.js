// 구현 화면의 배치와 상태를 정리한 와이어프레임임. 실제 모션은 소개 페이지에서 확인함.
(() => {
  const C = {
    ink: '#182031',
    paper: '#f7f2e9',
    coral: '#ff796e',
    teal: '#174f54',
    sea: '#c8e1e0',
    line: '#d7d9d5',
    muted: '#637376',
  };
  const frames = [];
  const box = (name, x, y, w, h, fill, radius = 0) => ({
    type: 'rect',
    name,
    x,
    y,
    w,
    h,
    fill,
    radius,
  });
  const text = (value, x, y, w, size = 24, fill = C.ink, weight = 400) => ({
    type: 'text',
    name: value.split('\n')[0],
    text: value,
    x,
    y,
    w,
    h: size * (size >= 35 ? 1.18 : 1.5) * value.split('\n').length,
    lineHeight: size >= 35 ? 1.18 : 1.5,
    size,
    fill,
    weight,
  });
  const photo = (src, x, y, w, h, name) => ({
    type: 'image',
    name,
    src: `../design/${src}`,
    x,
    y,
    w,
    h,
    radius: 18,
  });
  const button = (label, x, y, w = 220, fill = C.teal, color = 'white') => [
    box(label, x, y, w, 52, fill, 26),
    {
      ...text(label, x + (w <= 64 ? 0 : 18), y + 14, w <= 64 ? w : w - 36, 18, color, 600),
      align: w <= 64 ? 'center' : 'left',
    },
  ];
  const frame = (id, title, notes, w = 1920, h = 1080, bg = C.paper) => {
    const f = { id, title, notes, w, h, bg, nodes: [] };
    frames.push(f);
    return f.nodes;
  };
  const heading = (n, eyebrow, title, body) => [
    text(n, 96, 72, 1700, 16, C.muted, 600),
    text(eyebrow, 96, 200, 780, 18, C.teal, 600),
    text(title, 96, 270, 760, 76, C.ink, 700),
    text(body, 96, 530, 760, 27, C.muted),
  ];
  const credit = (label, y = 1000) => text(label, 96, y, 1728, 14, C.muted);

  let n = frame(
    'intro-01',
    '01 · 소개 / 오프닝',
    'FHD 1920×1080. 오프닝 재생·다시 보기, 지도 진입. 도시별 사진·메타·출처를 함께 교체함.',
  );
  n.push(
    photo('kyoto.jpg', 0, 0, 1920, 1080, '대표 도시 사진'),
    box('사진 명암 오버레이', 0, 0, 1920, 1080, '#142d39', 0),
  );
  n[1].opacity = 0.55;
  n.push(
    text('TripeE', 96, 38, 300, 32, 'white', 700),
    text('도시 탐색       제작 노트       출처·운영 정보 ↗', 1160, 46, 670, 18, 'white'),
    text('A WORLD OF POSSIBILITIES', 96, 260, 800, 18, 'white'),
    text('다음 여행은\n어디로?', 96, 320, 1120, 132, 'white', 700),
    text('지도 위의 한 점이,\n당신의 다음 여행이 되는 곳.', 100, 660, 600, 28, 'white'),
    ...button('도시 탐색하기 ↗', 100, 780, 250, C.coral, C.ink),
    text('FEATURED CITY / 01\n교토, 일본 · Kyoto, Japan', 1440, 800, 380, 24, 'white'),
    text('오프닝 다시 보기 ↻', 1510, 910, 300, 16, 'white'),
    text('01 — DISCOVER                    SCROLL TO EXPLORE ↓', 96, 1010, 1100, 16, 'white'),
    text('사진 출처 ↗', 1640, 1010, 200, 14, 'white'),
  );
  for (const expanded of [false, true]) {
    n = frame(
      expanded ? 'intro-02-b' : 'intro-02-a',
      `02 · 도시 발견 / ${expanded ? '전환 후' : '전환 전'}`,
      '스크롤 기준 통과 시 약 2초 자동 전환. 위로 돌아오면 역전환. 예시 바로 보기 제공. 한글 3자·영문 8자 이내 후보만 사용함.',
    );
    n.push(
      ...heading(
        '02 / FIND YOUR CITY',
        '도시를 발견하는 순간',
        '도시를 고르는 순간,\n여행이 시작돼요.',
        '어떤 풍경을 만나고 싶은가요?\n지도를 움직이며 다음 여행지를 찾아보세요.',
      ),
      ...button('지도로 여행 시작하기 ↗', 96, 670, 300),
    );
    if (expanded) {
      n.push(
        photo('beijing.jpg', 980, 220, 844, 400, '베이징 천단 사진'),
        box('제목 영역 명암', 980, 490, 844, 130, C.ink, 0),
      );
      n.at(-1).opacity = 0.7;
      n.push(
        text('CHINA / 北京', 1010, 508, 400, 14, 'white'),
        text('베이징 Beijing', 1010, 545, 470, 45, 'white', 700),
        text('이 도시 살펴보기 ↗', 1590, 558, 220, 18, 'white'),
      );
    }
    const panelY = expanded ? 632 : 220,
      panelH = expanded ? 174 : 586;
    ['대표 경험', '방문 시기', '안전정보'].forEach((label, i) => {
      n.push(
        box(label, 980 + i * 285, panelY, 274, panelH, ['#bdd9df', '#efaa80', '#ebd698'][i], 12),
      );
      if (expanded)
        n.push(
          text(`0${i + 1}`, 1000 + i * 285, panelY + 18, 220, 14),
          text(label, 1000 + i * 285, panelY + 50, 220, 25, C.ink, 700),
          text(
            [
              '풍경과 문화, 직접 해보고 싶은 일',
              '계절별 특징과 행사 일정',
              '공식 출처·확인일·미확인 항목',
            ][i],
            1000 + i * 285,
            panelY + 96,
            220,
            17,
          ),
        );
      else
        n.push(
          text(['GO', '39.90° N\n116.41° E', 'SEE'][i], 1010 + i * 285, 275, 220, 32, C.teal, 600),
        );
    });
    if (!expanded) n.push(text('베이징 Beijing', 1220, 470, 420, 40, '#482e29', 700));
    n.push(
      text('도시 정보 구성 예시 · 사진 출처 ↗', 980, 830, 844, 15, C.muted),
      text(
        expanded
          ? '마음에 드는 도시를 지도에서 만나보세요.'
          : '아래로 스크롤하면 여행이 펼쳐져요 ↓',
        980,
        885,
        580,
        18,
        C.muted,
      ),
      ...button('예시 바로 보기 ↗', 1600, 870, 224, '#e4e6dc', C.ink),
    );
  }
  n = frame(
    'intro-03',
    '03 · 기록 / 콘텐츠 검토 흐름',
    'NASA 2016 관측자료 영상. 화면 밖·비활성 탭에서 정지함. 재생·정지 제공. 운영 흐름은 구현 예정임.',
    1920,
    1080,
    '#111522',
  );
  n.push(
    text('03 / BEHIND THE JOURNEY', 96, 70, 1400, 18, '#e1d9ca'),
    text('세상을 연결하는\n수많은 기록.', 96, 230, 780, 78, '#fff7e8', 700),
    text('자료 수집 → AI 정리 → 관리자 검토 → 게시', 96, 510, 820, 27, '#ffad96', 600),
    text(
      '01  공식 자료와 사진 출처 확보\n02  규격에 따라 요약하고 근거 연결\n03  사실·최신성·사진 이용 조건 확인\n04  확인한 정보와 미확인 항목 구분',
      96,
      600,
      800,
      24,
      '#e2e0dc',
    ),
    photo('earth-night-2016.jpg', 960, 250, 864, 500, 'NASA 야간 지구 / 영상 자리'),
    text('2016 / BLACK MARBLE · 공통 관측자료', 1000, 790, 760, 19, 'white'),
    ...button('영상 재생 / 정지', 1000, 850, 240, '#344259'),
    text('NASA’s Goddard Space Flight Center · 출처 ↗', 960, 935, 850, 15, '#b2b7c4'),
  );
  for (const guide of [false, true]) {
    n = frame(
      guide ? 'intro-04-b' : 'intro-04-a',
      `04 · 관점 / ${guide ? '정보 카드가 앞' : '풍경 카드가 앞'}`,
      '앞 카드를 아래로 빼고 뒤로 넣음. 좌우로 벌어지거나 사진이 바뀌지 않음. 장면 상단 6%에서 전환, 45%에서 역전환. 낮은 화면은 버튼으로 전환함.',
    );
    n.push(
      text('04 / A DIFFERENT PERSPECTIVE', 96, 72, 1600, 18, C.muted),
      box('뒤 카드', 140, 195, 680, 640, '#d5e1db', 24),
    );
    if (guide) {
      n.push(
        box('정보 카드', 175, 230, 650, 640, 'white', 24),
        photo('naples.jpg', 175, 230, 650, 320, '나폴리 도시 풍경'),
        text('도시 정보 구성 예시', 205, 575, 590, 16, C.muted),
        text('설렘 다음에, 알아둘 것들.', 205, 615, 590, 32, C.ink, 700),
        text(
          '대표 경험   무엇을 보고, 어떤 일을 할까\n방문 시기   계절의 특징과 행사 일정\n안전정보   공식 출처·확인일과 미확인 항목',
          205,
          680,
          590,
          22,
        ),
        text('실제 여행 정보가 아닌 항목 예시입니다.', 205, 815, 590, 16, C.muted),
      );
    } else {
      n.push(
        photo('naples-castle.jpg', 175, 230, 650, 640, '나폴리 풍경 카드'),
        box('카드 명암', 175, 650, 650, 220, C.ink, 0),
      );
      n.at(-1).opacity = 0.65;
      n.push(
        text('01 / FEEL THE CITY', 210, 680, 570, 16, 'white'),
        text('낯선 길에서,\n머물고 싶은 순간.', 210, 720, 570, 44, 'white', 700),
        text('Naples, Italy', 210, 830, 550, 18, 'white'),
      );
    }
    n.push(
      text('WONDER.', 1030, 270, 740, 96, '#bbcbc3', 700),
      text('여행의 기대와\n준비를 함께.', 1030, 430, 740, 72, C.ink, 700),
      text(
        '마음에 드는 도시를 찾았다면,\n방문 시기와 이동 방법도 함께 살펴보세요.',
        1030,
        660,
        740,
        25,
        C.muted,
      ),
      ...button('도시 정보 살펴보기 ↗', 1030, 800, 300),
      text(
        guide ? '02 / 알아두면 좋은 것들' : '01 / 풍경에서 시작하기',
        175,
        920,
        400,
        17,
        C.muted,
      ),
      ...button(guide ? '풍경 카드 보기 ↗' : '정보 카드 보기 ↗', 570, 900, 255, '#e6e5db', C.ink),
      credit('사진 · Jebulon / CC0 · Francesco Russo / Public domain · 크롭·명암 조정'),
    );
  }
  n = frame(
    'intro-05',
    '05 · 준비 / 움직이는 도형',
    '스크롤 진행에 따라 꽃·나침반·창문 도형이 회전·변형됨. 동작 줄이기 설정에서는 정적으로 표시함.',
    1920,
    1080,
    '#112d39',
  );
  n.push(
    text('05 / BEFORE YOU GO', 96, 70, 1700, 18, '#dbe2de'),
    text('좋아하는 이유부터,\n알아둘 것까지.', 96, 170, 1700, 76, '#fff6e9', 700),
  );
  ['무엇을 경험할까', '어떻게 준비할까', '무엇을 확인할까'].forEach((label, i) => {
    const x = 96 + i * 582;
    n.push(
      box('도형 변형 영역', x, 460, 530, 280, ['#b2d4ce', '#efaa80', '#e8d698'][i], 48),
      text(
        ['꽃 · 모서리 → 곡선', '나침반 · 회전', '창문 · 간격 변화'][i],
        x + 40,
        580,
        450,
        30,
        C.teal,
        600,
      ),
      text(`0${i + 1} / ${['EXPERIENCE', 'PRACTICAL', 'SOURCES'][i]}`, x, 790, 530, 17, '#c1cfcf'),
      text(label, x, 840, 530, 36, '#fff6e9', 600),
      text(
        [
          '풍경과 문화, 도시에서 보내는 시간.',
          '방문 시기와 이동, 여행 전 고려사항.',
          '안전정보의 출처와 확인 시점.',
        ][i],
        x,
        910,
        530,
        23,
        '#c1cfcf',
      ),
    );
  });
  n = frame(
    'intro-06',
    '06 · 출발 / 도시 선택과 푸터',
    '3개 도시 티켓 순서도 새로고침마다 바뀜. 도시 링크는 지도 상세로 이동함. 출처·라이선스는 접힌 푸터에서 확인함.',
  );
  n.push(
    ...heading(
      '06 / CHOOSE YOUR FIRST STOP',
      '이제, 당신의 차례',
      '여행의 시작점을\n골라보세요.',
      '막연했던 설렘을 지도 위의 한 곳으로.\n어떤 도시에서 이야기를 시작할까요?',
    ),
    ...button('전체 지도 펼치기 ↗', 96, 680, 280),
  );
  ['교토  Kyoto', '베이징  Beijing', '나폴리  Naples'].forEach((city, i) => {
    n.push(
      box('도시 티켓', 1000, 210 + i * 210, 824, 180, ['#b9d7d1', '#e9b78c', '#dfd5b7'][i], 22),
      text(`0${i + 1} / ${['JAPAN', 'CHINA', 'ITALY'][i]}`, 1040, 240 + i * 210, 680, 16),
      text(city, 1040, 288 + i * 210, 650, 44, C.ink, 700),
      text('도시 살펴보기 ↗', 1550, 340 + i * 210, 240, 18),
    );
  });
  n.push(
    box('푸터 경계', 96, 950, 1728, 1, C.line),
    text('TripeE', 96, 990, 260, 26, C.ink, 700),
    text('출처·라이선스 ⌄', 750, 990, 420, 20),
    text('포트폴리오 · 탐색 시안', 1460, 990, 360, 18, C.muted),
  );

  function mapBase(id, title, notes, w = 1920, h = 1080) {
    const mobile = w < 700,
      n = frame(id, title, notes, w, h, C.sea);
    // 실제 지리 경계가 아닌 기능 배치용 개략 지도임.
    n.push(
      box('세계 지도 영역', 0, 0, w, h, C.sea),
      text(
        '세계 지도 · MapLibre / OpenFreeMap',
        mobile ? 30 : 630,
        mobile ? 90 : 120,
        mobile ? 330 : 900,
        mobile ? 14 : 24,
        '#557e7f',
        600,
      ),
    );
    const pts = mobile
      ? [
          [70, 160, 8],
          [220, 310, 16],
          [130, 380, 1],
          [280, 180, 4],
        ]
      : [
          [400, 300, 12],
          [750, 440, 8],
          [1080, 330, 24],
          [1400, 520, 16],
          [700, 700, 1],
          [1250, 700, 1],
        ];
    pts.forEach(([x, y, count]) => {
      n.push(
        box(count === 1 ? '개별 핀' : '묶음 핀', x, y, count === 1 ? 38 : 64, 44, C.teal, 12),
        text(count === 1 ? '↗' : String(count), x + 11, y + 11, 50, 18, 'white', 600),
      );
    });
    n.push(
      box('브랜드', mobile ? 14 : 32, mobile ? 14 : 28, mobile ? 114 : 154, 48, C.paper, 24),
      text('TripeE', mobile ? 32 : 56, mobile ? 27 : 40, 120, mobile ? 21 : 26, C.teal, 700),
    );
    n.push(...button('⌕', w - (mobile ? 62 : 84), mobile ? 14 : 28, 48, C.paper, C.teal));
    const footer = mobile
      ? '© OpenMapTiles · © OpenStreetMap'
      : '© OpenMapTiles · © OpenStreetMap contributors';
    n.push(
      text(footer, 12, h - 29, w - 100, mobile ? 10 : 13, C.teal),
      text(
        '기타 출처 ↗',
        w - (mobile ? 78 : 132),
        h - 29,
        mobile ? 75 : 120,
        mobile ? 10 : 13,
        C.teal,
      ),
    );
    return n;
  }
  function recent(n, w, h) {
    const m = w < 700;
    n.push(
      box(
        '최근 탐색·크기 설정',
        m ? 14 : 32,
        h - (m ? 154 : 168),
        m ? w - 28 : 420,
        m ? 110 : 122,
        C.paper,
        20,
      ),
      text('최근 본 도시', m ? 30 : 52, h - (m ? 137 : 150), 330, m ? 13 : 15, C.muted),
      text(
        '교토       베이징       나폴리',
        m ? 30 : 52,
        h - (m ? 108 : 116),
        340,
        m ? 16 : 19,
        C.teal,
        600,
      ),
      text(
        '핀 크기     ━━━━━●━━   100%',
        m ? 30 : 52,
        h - (m ? 74 : 78),
        340,
        m ? 12 : 15,
        C.muted,
      ),
    );
  }
  n = mapBase(
    'map-pc',
    '07 · PC / 지도 기본',
    '1920×1080. 핀보다 최근 도시·검색·로딩을 앞에 배치함. 최근 본 도시 최대 3개. 크기 75~150%, 터치 최소 44px.',
  );
  recent(n, 1920, 1080);
  n.push(
    ...button('+', 1836, 850, 48, C.paper, C.teal),
    ...button('−', 1836, 912, 48, C.paper, C.teal),
    text('묶음 호버 / 포커스 : 24개 도시 · 9개 국가', 960, 275, 600, 20, C.teal),
  );
  function group(n, w, h, compact = false) {
    const m = w < 700,
      x = m ? 12 : w - 452,
      y = m ? (compact ? 82 : 246) : 96,
      pw = m ? w - 24 : 420,
      ph = h - y - 44;
    n.push(box('선택한 도시 목록 패널', x, y, pw, ph, '#fffdf8', 20));
    if (!compact)
      n.push(
        photo('kyoto.jpg', x, y, pw, m ? 142 : 200, '묶음 표지 / 교토 포함'),
        text('사진 출처 ↗', x + 16, y + (m ? 148 : 208), pw - 32, 12, C.muted),
      );
    n.push(...button('×', x + pw - 58, y + 10, 44, '#fffdf8', C.teal));
    const top = y + (compact ? 58 : m ? 176 : 238);
    n.push(
      text('선택한 도시 16곳', x + 18, top, pw - 36, m ? 22 : 26, C.ink, 700),
      text('4개 국가 · 가까운 도시부터 표시', x + 18, top + 36, pw - 36, 14, C.muted),
      ...button('지도에 모아 보기 ↗', x + 18, top + 68, pw - 36, '#dfecea', C.teal),
      box('목록 검색창', x + 18, top + 136, pw - 36, 42, '#f0f3ef', 12),
      text('목록에서 도시 검색', x + 30, top + 147, pw - 60, 14, C.muted),
    );
    n.push(text('전체 국가 ⌄     일본     중국', x + 22, top + 190, pw - 44, 13, C.teal));
    const rowY = top + 220;
    ['교토 · 일본', '오사카 · 일본', '오쓰 · 일본', '베이징 · 중국', '서울 · 대한민국'].forEach(
      (name, i) => {
        if (rowY + i * 55 + 47 < y + ph - 16)
          n.push(
            box('도시 선택 행', x + 18, rowY + i * 55, pw - 36, 47, '#f1f2eb', 10),
            text(name, x + 32, rowY + i * 55 + 13, pw - 64, 17, C.ink, 500),
          );
      },
    );
  }
  n = mapBase(
    'map-group',
    '08 · PC / 묶음 선택',
    '국경에 관계없이 밀집 핀을 묶음. 사진 카드에서 도시 선택. 지도에 모아 보기는 현재 필터 결과만 대상으로 함. 빈 결과일 때 비활성화함.',
  );
  recent(n, 1920, 1080);
  group(n, 1920, 1080);
  function detail(n, w, h) {
    const m = w < 700,
      x = m ? 10 : w - 454,
      y = m ? 190 : 92,
      pw = m ? w - 20 : 430,
      ph = h - y - 38;
    n.push(
      box('도시 상세 패널', x, y, pw, ph, '#fffdf8', 20),
      text('← 선택한 도시 목록', x + 18, y + 18, pw - 82, 16, C.teal, 600),
      text('×', x + pw - 40, y + 13, 30, 26, C.teal),
      photo('kyoto.jpg', x + 18, y + 64, pw - 36, m ? 170 : 220, '교토 사진 슬라이드'),
      text(
        '‹                       1 / 3                       ›',
        x + 30,
        y + (m ? 198 : 248),
        pw - 60,
        16,
        'white',
      ),
      text('교토  Kyoto', x + 18, y + (m ? 258 : 310), pw - 36, 30, C.ink, 700),
      text('일본 · 전통과 골목의 풍경', x + 18, y + (m ? 300 : 352), pw - 36, 16, C.muted),
      text(
        '경험        방문 시기        안전정보',
        x + 18,
        y + (m ? 340 : 400),
        pw - 36,
        15,
        C.teal,
        600,
      ),
      text(
        '대표 경험\n\n검토된 도시 정보와 근거 표시\n\n미확인 정보는 별도로 구분함',
        x + 18,
        y + (m ? 390 : 465),
        pw - 36,
        m ? 17 : 22,
      ),
    );
  }
  n = mapBase(
    'map-detail',
    '09 · PC / 도시 상세',
    '사진은 한 장씩 슬라이드. 목록 복귀·닫기·Escape 시 필터/스크롤/선택 행 포커스 복원. 검색 이동은 패널에 가려지지 않도록 좌표 보정함.',
  );
  detail(n, 1920, 1080);
  n = mapBase(
    'map-search',
    '10 · PC / 검색 펼침',
    '검색 아이콘에서 입력창으로 확장. 도시/나라 검색, 방향키·Enter·Escape 지원. 선택 후 입력창을 접음.',
  );
  recent(n, 1920, 1080);
  n.push(
    box('확장 검색', 1320, 28, 568, 56, '#fffdf8', 28),
    text('교토', 1342, 45, 460, 20),
    text('⌕', 1838, 42, 36, 26, C.teal),
    box('검색 결과', 1320, 94, 568, 160, '#fffdf8', 20),
    text('교토 · 일본\nKyoto\n도시 상세 열기 ↗', 1344, 118, 520, 22, C.teal),
  );
  for (const type of ['base', 'group', 'detail', 'search']) {
    n = mapBase(
      `mobile-${type}`,
      `11 · 모바일 / ${{ base: '지도 기본', group: '도시 목록', detail: '도시 상세', search: '검색·키보드' }[type]}`,
      '390×844. 목록·상세가 열리면 최근 도시를 숨겨 터치 충돌 방지. 출처는 패널 아래 유지. 실제 키보드에 맞춰 검색 결과 높이 조절함.',
      390,
      844,
    );
    if (type === 'base') recent(n, 390, 844);
    if (type === 'group') group(n, 390, 844);
    if (type === 'detail') detail(n, 390, 844);
    if (type === 'search') {
      n.push(
        box('확장 검색', 14, 14, 362, 50, '#fffdf8', 25),
        text('교토', 34, 30, 270, 18),
        text('⌕', 334, 27, 30, 24, C.teal),
        box('검색 결과', 14, 76, 362, 150, '#fffdf8', 16),
        text('교토 · 일본\nKyoto\n도시 상세 열기 ↗', 34, 97, 320, 18, C.teal),
        box('기기 가상 키보드 영역', 0, 525, 390, 319, '#d3d7da'),
        text('가상 키보드\n기기에서 별도 검수', 30, 640, 330, 24, C.muted),
      );
    }
  }
  n = mapBase(
    'mobile-compact',
    '12 · 작은 화면 / 목록',
    '320×568. 높이 700px 이하에서는 카드 전체 스크롤+상단 닫기 고정. 500px 이하에서는 사진을 숨겨 목록 공간 확보.',
    320,
    568,
  );
  group(n, 320, 568, true);
  for (const expanded of [false, true]) {
    n = frame(
      `mobile-intro-${expanded ? 'after' : 'before'}`,
      `모바일 소개 / 섹션 2 ${expanded ? '전환 후' : '전환 전'}`,
      '390×844 기준 장면. 문구·지도 예시는 세로 배치. 긴 이름은 후보에서 제외하며 도시 이동 버튼은 제목 아래 별도 행에 둠.',
      390,
      844,
    );
    n.push(
      text('02 / FIND YOUR CITY', 24, 32, 342, 12, C.muted),
      text('도시를 고르는 순간,\n여행이 시작돼요.', 24, 78, 342, 35, C.ink, 700),
      text(
        '어떤 풍경을 만나고 싶은가요?\n지도를 움직이며 다음 여행지를 찾아보세요.',
        24,
        197,
        342,
        16,
        C.muted,
      ),
      ...button('지도로 여행 시작하기 ↗', 24, 268, 290),
    );
    if (expanded) {
      n.push(
        photo('beijing.jpg', 24, 352, 342, 252, '도시 사진'),
        box('사진 제목 배경', 24, 483, 342, 121, C.ink, 0),
      );
      n.at(-1).opacity = 0.7;
      n.push(
        text('CHINA / 北京', 42, 495, 300, 10, 'white'),
        text('베이징 Beijing', 42, 520, 300, 30, 'white', 700),
        text('이 도시 살펴보기 ↗', 42, 566, 280, 14, 'white'),
      );
    }
    ['대표 경험', '방문 시기', '안전정보'].forEach((label, i) => {
      n.push(
        box(
          label,
          24 + i * 117,
          expanded ? 614 : 352,
          108,
          expanded ? 139 : 401,
          ['#bdd9df', '#efaa80', '#ebd698'][i],
          10,
        ),
        text(
          expanded ? label : ['GO', '39.90° N', 'SEE'][i],
          34 + i * 117,
          expanded ? 649 : 398,
          88,
          expanded ? 17 : 15,
          C.teal,
          600,
        ),
      );
    });
    if (!expanded) n.push(text('베이징 Beijing', 91, 524, 250, 27, '#482e29', 700));
    n.push(
      text('도시 정보 구성 예시 · 사진 출처 ↗', 24, 772, 342, 11, C.muted),
      text('예시 바로 보기 ↗', 242, 805, 130, 13, C.teal),
    );
  }
  n = frame(
    'states',
    '13 · 예외 상태와 조작 규칙',
    '화면 구현 기준. 아래 정보는 검토를 위한 주석이며 서비스의 추가 안내 문구가 아님.',
  );
  const states = [
    ['지도 연결 중', '비행기 로딩 애니메이션\n도시 핀보다 앞에 표시함.'],
    ['지도 오류', '오류 안내 + 다시 시도\n도시 검색은 계속 사용할 수 있음.'],
    ['도시 콘텐츠 준비 중', '핵심 항목 미충족 시 준비 중 표시\n부족한 개별 항목은 미확인 표시.'],
    ['검색 결과 없음', '조건에 맞는 도시 없음\n지도에 모아 보기 비활성화.'],
    ['사진을 불러오지 못함', '대체 안내 표시\n도시 선택 기능은 유지함.'],
    ['동작 줄이기', '소개 전환은 완성 화면으로 제공\n묶음 핀 전환은 즉시 반영함.'],
  ];
  n.push(text('검수할 상태와 조작 기준', 96, 70, 1700, 54, C.ink, 700));
  states.forEach(([title, body], i) => {
    const x = 96 + (i % 3) * 582,
      y = 210 + Math.floor(i / 3) * 330;
    n.push(
      box(title, x, y, 530, 270, 'white', 20),
      text(title, x + 30, y + 32, 470, 30, C.teal, 700),
      text(body, x + 30, y + 100, 470, 24, C.muted),
    );
  });
  n.push(
    credit('화면 간 이동: 지도 → 묶음 목록 → 도시 상세 → 기존 목록. 필터·스크롤·포커스를 유지함.'),
  );
  n = frame(
    'rotation',
    '14 · 도시 예제·출처·전환 규칙',
    '2026-11-13 기준. 구현과 와이어프레임의 상태를 함께 검수하기 위한 기록임.',
  );
  n.push(
    text('소개 페이지 도시 예제', 96, 80, 1700, 58, C.ink, 700),
    text('나폴리 / 교토 / 베이징', 96, 180, 1700, 40, C.teal, 600),
    text(
      '01  오프닝 · 섹션 2 · 섹션 4는 서로 다른 도시를 표시함.\n02  같은 탭에서 새로고침하면 각 위치에 직전 도시를 다시 표시하지 않음.\n03  이름 · 사진 · 출처 · 좌표 · 도시 링크를 한 묶음으로 바꿈.\n04  섹션 2는 한글 1~3자, 공백 없는 영문 1~8자 후보만 사용함.\n05  섹션 4 앞뒤 사진은 같은 도시의 서로 다른 풍경을 사용함.\n06  섹션 6은 3개 도시를 유지하고 순서를 바꿈.',
      96,
      285,
      1700,
      30,
    ),
    text(
      '저장소 차단 환경에서는 무작위 표시를 유지하되 직전 도시 제외는 보장하지 못함.\n추가 도시를 넣을 때는 사진 이용 조건·한영 이름·링크·모바일 전환을 함께 검수함.\n기록 사진은 현재 현장 상황이나 검토 완료된 여행 정보를 의미하지 않음.',
      96,
      660,
      1700,
      24,
      C.muted,
    ),
    text('출처 표시', 96, 830, 700, 32, C.ink, 700),
    text(
      '지도 필수 출처는 지도 하단에 유지함. 사진 상세 출처와 외부 라이선스는 소개 푸터로 연결함.\n와이어프레임의 지도는 배치 설명용 개략 영역이며 실제 지리 경계를 표현하지 않음.',
      96,
      890,
      1700,
      24,
      C.muted,
    ),
  );
  globalThis.TripeEWireframes = { version: '2026-11-13', font: 'Pretendard', frames };
})();

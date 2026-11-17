// 별도 장면을 덮어씌우지 않고 같은 세 면의 크기와 위치를 바꿔 정보 영역으로 연결함.
if (window.gsap && window.ScrollTrigger) {
  const section = document.querySelector('.bridge');
  const visual = section.querySelector('.fold-visual');
  const photoTitle = visual.querySelector('.fold-photo-title');
  const skip = visual.querySelector('.fold-skip');
  const media = gsap.matchMedia();
  media.add(
    {
      desktop: '(min-width: 901px)',
      mobile: '(max-width: 900px)',
      short: '(max-height: 699px)',
      reduced: '(prefers-reduced-motion: reduce)',
    },
    ({ conditions }) => {
      photoTitle.inert = false;
      if (conditions.reduced) return;
      visual.classList.add('fold-animated');
      const desktop = conditions.desktop;
      const stage = visual.querySelector('.fold-stage');
      const sheets = gsap.utils.toArray('.fold-sheet');
      const photograph = visual.querySelector('.fold-photograph');
      const cityName = photoTitle.querySelector('h3');
      const tileHeight = () =>
        parseFloat(getComputedStyle(visual).getPropertyValue('--fold-panel-height'));
      const timeline = gsap.timeline({ paused: true });
      timeline.timeScale(1.4);
      timeline
        .fromTo(
          sheets,
          {
            top: 0,
            height: () => stage.clientHeight,
            rotationY: (i) => (i === 1 ? -12 : 12),
            transformPerspective: 1000,
          },
          { rotationY: 0, duration: 0.65, ease: 'power1.inOut' },
          0.2,
        )
        .fromTo('.fold-panel-landscape', { autoAlpha: 1 }, { autoAlpha: 0, duration: 0.55 }, 0.8)
        .to(
          sheets,
          {
            top: () => stage.clientHeight - tileHeight(),
            height: tileHeight,
            stagger: 0.07,
            duration: 1.15,
            ease: 'power2.inOut',
          },
          0.85,
        )
        .fromTo(
          '.fold-photograph img',
          { scale: 1.13 },
          { scale: 1, duration: 1.7, ease: 'power1.out' },
          0.6,
        )
        .fromTo(
          cityName,
          {
            x: () =>
              stage.clientWidth / 2 -
              photoTitle.offsetLeft -
              cityName.offsetLeft -
              cityName.offsetWidth / 2,
            y: () =>
              stage.clientHeight / 2 -
              photoTitle.offsetTop -
              cityName.offsetTop -
              cityName.offsetHeight / 2,
            scale: desktop ? 0.7 : 0.68,
            color: '#482e29',
          },
          { x: 0, y: 0, scale: 1, duration: 1.15, ease: 'power2.inOut' },
          0.85,
        )
        .to(
          cityName,
          { color: '#fffaf1', textShadow: '0 2px 8px #061c2b70', duration: 0.18 },
          desktop ? 1.53 : 1.4,
        )
        .fromTo(
          '.fold-photo-title > span, .fold-photo-title > a',
          { autoAlpha: 0, y: 8 },
          { autoAlpha: 1, y: 0, duration: 0.4, stagger: 0.1 },
          1.9,
        )
        .fromTo(
          '.fold-panel-content',
          { autoAlpha: 0, y: 12 },
          { autoAlpha: 1, y: 0, stagger: 0.12, duration: 0.45 },
          2.05,
        );
      // 사진의 각 열을 해당 면의 이동과 같은 박자로 열어 틈새에서 먼저 비치지 않게 함.
      ['--reveal-left', '--reveal-center', '--reveal-right'].forEach((property, index) => {
        timeline.fromTo(
          photograph,
          { [property]: '0%' },
          {
            [property]: '100%',
            duration: 1.15,
            ease: 'power2.inOut',
          },
          0.85 + index * 0.07,
        );
      });
      const progress = visual.querySelector('.fold-track i');
      const hint = visual.querySelector('.fold-scroll-hint');
      let expanded = false;
      const update = () => {
        photoTitle.inert = timeline.time() < 2.3;
        progress.style.transform = `scaleX(${timeline.progress()})`;
      };
      const settle = () => {
        visual.dataset.foldState = expanded ? 'expanded' : 'folded';
        hint.textContent = expanded
          ? '마음에 드는 도시를 지도에서 만나보세요.'
          : '아래로 스크롤하면 여행이 펼쳐져요 ↓';
      };
      timeline.eventCallback('onUpdate', update);
      timeline.eventCallback('onComplete', settle);
      timeline.eventCallback('onReverseComplete', settle);
      timeline.progress(0).pause();
      update();
      settle();
      const change = (next) => {
        if (expanded === next) return;
        expanded = next;
        visual.dataset.foldState = 'transitioning';
        hint.textContent = next
          ? '도시의 이야기를 펼치는 중이에요.'
          : '여행의 시작점으로 돌아가는 중이에요.';
        if (next) timeline.play();
        else timeline.reverse();
      };
      // 재생과 되감기 기준을 분리해 경계 부근의 작은 스크롤에는 상태가 바뀌지 않게 함.
      const trigger = ScrollTrigger.create({
        id: 'city-discovery',
        trigger: visual,
        start: 'top 50%',
        end: 'top 30%',
        onUpdate: (self) => {
          if (self.scroll() >= self.end) change(true);
          else if (self.scroll() <= self.start) change(false);
        },
        onRefresh: (self) => {
          if (self.scroll() >= self.end) expanded = true;
          else if (self.scroll() <= self.start) expanded = false;
          // 크기 변경과 폰트 로딩 시 새 위치를 계산하되 재생을 반복하지 않음.
          timeline
            .invalidate()
            // 0 지점에서도 시작 좌표를 다시 계산하도록 한 번 렌더링함.
            // 글꼴 로딩 전 폭이 남으면 도시명이 바뀔 때 중앙에서 벗어날 수 있음.
            .progress(1, true)
            .progress(expanded ? 1 : 0, true)
            .pause();
          update();
          settle();
        },
      });
      const jump = () => {
        expanded = true;
        timeline.progress(1).pause();
        update();
        settle();
        photoTitle.querySelector('a').focus({ preventScroll: true });
      };
      skip.addEventListener('click', jump);
      return () => {
        trigger.kill();
        skip.removeEventListener('click', jump);
        delete visual.dataset.foldState;
        progress.style.removeProperty('transform');
        hint.textContent = '아래로 스크롤하면 여행이 펼쳐져요 ↓';
        visual.classList.remove('fold-animated');
        photoTitle.inert = false;
      };
    },
  );
  document.fonts.ready.then(() => ScrollTrigger.refresh());
}

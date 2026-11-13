// 앞 카드를 아래로 빼고 뒤에 넣음. 두 카드가 떨어진 순간에만 순서를 바꿈.
if (window.gsap && window.ScrollTrigger) {
  const deck = document.querySelector('.journey-deck');
  const mood = deck.querySelector('.journey-mood');
  const guide = deck.querySelector('.journey-guide');
  const button = deck.querySelector('.journey-deck-toggle');
  const status = deck.querySelector('.journey-deck-status');
  const media = gsap.matchMedia();

  media.add('(prefers-reduced-motion: no-preference)', () => {
    deck.classList.add('is-stacked');
    let showingGuide = false;
    const crossingTime = 0.82;
    const restingShadow = '0px 18px 40px rgba(23, 33, 50, 0.15)';
    const tuckedShadow = '0px 10px 24px rgba(23, 33, 50, 0.10)';
    const timeline = gsap.timeline({ id: 'perspective-card-motion', paused: true });

    gsap.set([mood, guide], { transformPerspective: 1800, transformOrigin: '50% 50%' });
    gsap.set(mood, { rotation: -3, boxShadow: restingShadow });
    gsap.set(guide, { xPercent: 4, y: -12, rotation: 4, scale: 0.95, boxShadow: tuckedShadow });
    timeline
      .to(mood, {
        yPercent: 4,
        rotation: -2,
        scale: 1.015,
        boxShadow: '0px 30px 54px rgba(23, 33, 50, 0.20)',
        duration: 0.22,
        ease: 'sine.out',
      })
      .to(
        mood,
        {
          xPercent: 0,
          yPercent: 63,
          rotation: 0,
          rotationX: -65,
          scale: 0.94,
          duration: 0.6,
          ease: 'sine.inOut',
        },
        0.22,
      )
      .to(
        guide,
        {
          xPercent: 0,
          yPercent: -3,
          y: 0,
          rotation: 0,
          scale: 0.98,
          boxShadow: restingShadow,
          duration: 0.6,
          ease: 'sine.inOut',
        },
        0.22,
      )
      // 앞 카드가 아래쪽으로 완전히 빠져나왔을 때 뒤로 보냄.
      .set(mood, { zIndex: 1 }, crossingTime)
      .set(guide, { zIndex: 2 }, crossingTime)
      .to(
        mood,
        {
          xPercent: 4,
          yPercent: 0,
          y: -12,
          rotation: 4,
          rotationX: 0,
          scale: 0.95,
          boxShadow: tuckedShadow,
          duration: 0.78,
          ease: 'power2.inOut',
        },
        crossingTime,
      )
      .to(
        guide,
        {
          xPercent: 0,
          yPercent: 0,
          y: 0,
          rotation: -2,
          scale: 1,
          boxShadow: restingShadow,
          duration: 0.78,
          ease: 'power2.inOut',
        },
        crossingTime,
      );

    const sync = () => {
      const frontGuide = timeline.time() >= crossingTime;
      mood.inert = frontGuide;
      guide.inert = !frontGuide;
      mood.setAttribute('aria-hidden', String(frontGuide));
      guide.setAttribute('aria-hidden', String(!frontGuide));
    };
    const settle = () => {
      deck.dataset.card = showingGuide ? 'guide' : 'mood';
      status.textContent = showingGuide ? '02 / 여행 전에 알아두기' : '01 / 풍경에서 시작하기';
    };
    const change = (next) => {
      if (showingGuide === next) return;
      showingGuide = next;
      button.textContent = next ? '풍경 카드 보기 ↗' : '정보 카드 보기 ↗';
      deck.dataset.card = 'transitioning';
      if (next) timeline.play();
      else timeline.reverse();
    };
    timeline.eventCallback('onUpdate', sync);
    timeline.eventCallback('onComplete', settle);
    timeline.eventCallback('onReverseComplete', settle);
    sync();
    settle();

    const trigger = ScrollTrigger.create({
      id: 'perspective-cards',
      trigger: deck,
      start: 'top 45%',
      end: 'top 6%',
      onUpdate: (self) => {
        // 아래쪽 이동 공간까지 화면에 들어올 때만 자동으로 넘김.
        if (window.innerHeight < mood.offsetHeight * 1.25 + 100) return;
        if (self.scroll() >= self.end) change(true);
        else if (self.scroll() <= self.start) change(false);
      },
    });
    const toggle = () => change(!showingGuide);
    button.addEventListener('click', toggle);
    return () => {
      trigger.kill();
      button.removeEventListener('click', toggle);
      deck.classList.remove('is-stacked');
      delete deck.dataset.card;
      for (const card of [mood, guide]) {
        card.inert = false;
        card.removeAttribute('aria-hidden');
      }
    };
  });
  document.fonts.ready.then(() => ScrollTrigger.refresh());
}

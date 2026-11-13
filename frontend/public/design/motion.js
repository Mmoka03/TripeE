if (window.gsap && window.ScrollTrigger) {
  gsap.registerPlugin(ScrollTrigger);
  let opening;
  const media = gsap.matchMedia();
  media.add(
    {
      desktop: '(min-width: 901px)',
      mobile: '(max-width: 900px)',
      reduced: '(prefers-reduced-motion: reduce)',
    },
    ({ conditions }) => {
      const { desktop, reduced } = conditions;
      if (reduced) return;
      // 등장과 퇴장을 서로 다른 요소에 적용해 스크롤 중 재생 충돌을 방지함.
      opening = gsap
        .timeline()
        .from('.hero-photo img', { scale: 1.08, duration: 1.5, ease: 'power2.out' }, 0)
        .from(
          '.line > *',
          { yPercent: 110, duration: 0.85, stagger: 0.12, ease: 'power3.out' },
          0.1,
        )
        .from(
          '.hero-description, .hero-content .button',
          { autoAlpha: 0, y: 14, duration: 0.6, stagger: 0.1 },
          0.55,
        );
      if (desktop) {
        const exit = gsap.timeline({
          scrollTrigger: {
            id: 'hero-exit',
            trigger: '.hero',
            start: 'top top',
            end: '+=75%',
            pin: true,
            scrub: 0.25,
          },
        });
        // 퇴장을 75% 지점에 완료하고 잠깐의 여백 후 다음 장면으로 이동함.
        exit
          .to('.hero-content', { y: 90, autoAlpha: 0, duration: 0.75, ease: 'power1.in' }, 0)
          .to('.hero-meta, .hero-bottom', { autoAlpha: 0, duration: 0.5 }, 0)
          .to('.hero-photo', { scale: 1.04, duration: 1, ease: 'none' }, 0);
      }
      // 스크롤 위치에 연결해 정지 중에는 도형도 정지함. 역스크롤 시 원래 형태로 돌아감.
      gsap.utils.toArray('.lens-art').forEach((art, index) => {
        const shape = art.querySelector('.shape');
        const timeline = gsap.timeline({
          scrollTrigger: { trigger: art, start: 'top 95%', end: 'bottom 15%', scrub: 0.5 },
        });
        timeline.fromTo(
          shape,
          { rotation: -35, scale: 0.72 },
          { rotation: index === 1 ? 135 : 45, scale: 1, ease: 'none' },
          0,
        );
        if (index === 0)
          timeline.fromTo(
            shape.querySelectorAll('i'),
            { borderRadius: '10%' },
            { borderRadius: '50%', duration: 0.6 },
            0,
          );
        if (index === 2) timeline.fromTo(shape, { gap: '0px' }, { gap: '22px', duration: 1 }, 0);
        const dot = art.querySelector('.shape-dot');
        if (dot) timeline.to(dot, { y: 100, x: -25, scale: 0.6, duration: 1 }, 0);
      });
      gsap.utils.toArray('.city-ticket').forEach((ticket, index) => {
        gsap.from(ticket, {
          x: desktop ? 90 : 24,
          y: 30,
          rotation: desktop ? (index - 1) * 5 : 0,
          duration: 0.8,
          ease: 'power3.out',
          scrollTrigger: { trigger: ticket, start: 'top 92%', once: true },
        });
      });
      gsap.utils
        .toArray('.bridge-copy, .records-copy, .perspective-copy, .departure-copy')
        .forEach((item) => {
          gsap.from(item, {
            y: 28,
            autoAlpha: 0,
            duration: 0.7,
            ease: 'power2.out',
            scrollTrigger: { trigger: item, start: 'top 92%', once: true },
          });
        });
    },
  );
  document.querySelector('.replay').addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'instant' });
    opening?.restart();
  });
  document.fonts.ready.then(() => ScrollTrigger.refresh());
  window.addEventListener('load', () => ScrollTrigger.refresh());
} else {
  document.querySelector('.replay').hidden = true;
}

// 화면 안에서만 영상을 재생함. 모션 감소·데이터 절약 설정에서는 정지 이미지를 우선함.
const earthVideo = document.querySelector('.earth-video');
const earthToggle = document.querySelector('.earth-toggle');
if (earthVideo && earthToggle) {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let inView = false;
  let userPaused = false;
  let manualPlay = false;
  const shouldPlay = () =>
    inView &&
    !document.hidden &&
    !userPaused &&
    (manualPlay ||
      (!reducedMotion.matches &&
        !navigator.connection?.saveData &&
        !window.matchMedia('(max-width: 700px)').matches));
  const updatePlayback = () => {
    if (shouldPlay()) {
      if (!earthVideo.getAttribute('src')) earthVideo.src = earthVideo.dataset.src;
      earthVideo.play().catch(() => {
        earthToggle.textContent = '영상 재생';
      });
    } else earthVideo.pause();
  };
  const observer = new IntersectionObserver(
    ([entry]) => {
      inView = entry.isIntersecting;
      updatePlayback();
    },
    { threshold: 0.2 },
  );
  observer.observe(earthVideo);
  earthVideo.addEventListener('play', () => {
    earthToggle.textContent = '영상 멈춤';
    earthToggle.setAttribute('aria-pressed', 'true');
  });
  earthVideo.addEventListener('pause', () => {
    earthToggle.textContent = '영상 재생';
    earthToggle.setAttribute('aria-pressed', 'false');
  });
  earthVideo.addEventListener('error', () => {
    earthVideo.hidden = true;
    earthToggle.hidden = true;
  });
  earthToggle.addEventListener('click', () => {
    userPaused = !earthVideo.paused;
    manualPlay = !userPaused;
    updatePlayback();
  });
  reducedMotion.addEventListener('change', () => {
    manualPlay = false;
    updatePlayback();
  });
  document.addEventListener('visibilitychange', updatePlayback);
}

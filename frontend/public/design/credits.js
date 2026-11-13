// 지도에서 출처 링크로 진입하면 해당 항목을 펼쳐 바로 확인할 수 있게 함.
(() => {
  const footer = document.getElementById('credits');
  const details = footer?.querySelector('details');
  if (!details) return;

  const reveal = () => {
    const target = document.getElementById(location.hash.slice(1));
    if (!target || (target !== footer && !footer.contains(target))) return;
    details.open = true;
    requestAnimationFrame(() => {
      target.scrollIntoView({ block: 'center', behavior: 'instant' });
      if (target !== footer) target.focus({ preventScroll: true });
    });
  };
  reveal();
  window.addEventListener('hashchange', reveal);

  // 글꼴·스크롤 장면 배치가 확정된 뒤에도 연결된 출처 위치를 맞춤.
  // 사용자가 이미 조작했다면 늦은 로딩으로 화면을 다시 이동하지 않음.
  let interacted = false;
  for (const event of ['pointerdown', 'wheel', 'touchstart', 'keydown']) {
    window.addEventListener(
      event,
      () => {
        interacted = true;
      },
      { once: true, passive: true },
    );
  }
  const alignInitial = () => {
    if (!interacted) reveal();
  };
  document.fonts.ready.then(alignInitial);
  window.addEventListener('load', alignInitial, { once: true });
})();

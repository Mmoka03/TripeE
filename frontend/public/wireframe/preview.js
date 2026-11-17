(() => {
  const main = document.querySelector('main');
  const sheets = [];
  for (const frame of TripeEWireframes.frames) {
    const sheet = document.createElement('section');
    sheet.className = `sheet ${frame.w < 700 ? 'mobile' : ''}`;
    sheet.id = frame.id;
    const title = document.createElement('h2');
    title.textContent = frame.title;
    const notes = document.createElement('p');
    notes.className = 'notes';
    notes.textContent = frame.notes;
    const viewport = document.createElement('div');
    viewport.className = 'viewport';
    const canvas = document.createElement('div');
    canvas.className = 'canvas';
    Object.assign(canvas.style, {
      width: `${frame.w}px`,
      height: `${frame.h}px`,
      background: frame.bg,
    });
    for (const item of frame.nodes) {
      const node = document.createElement(item.type === 'image' ? 'img' : 'div');
      node.className = `node ${item.type === 'text' ? 'text' : item.type === 'image' ? 'photo' : ''}`;
      Object.assign(node.style, {
        left: `${item.x}px`,
        top: `${item.y}px`,
        width: `${item.w}px`,
        height: `${item.h}px`,
        borderRadius: `${item.radius || 0}px`,
        opacity: item.opacity ?? 1,
      });
      if (item.type === 'text') {
        node.textContent = item.text;
        Object.assign(node.style, {
          fontSize: `${item.size}px`,
          fontWeight: item.weight,
          lineHeight: item.lineHeight,
          textAlign: item.align || 'left',
          color: item.fill,
        });
      }
      if (item.type === 'rect') node.style.background = item.fill;
      if (item.type === 'image') {
        node.src = item.src;
        node.alt = item.name;
        node.loading = 'lazy';
      }
      canvas.append(node);
    }
    viewport.append(canvas);
    sheet.append(title, notes, viewport);
    main.append(sheet);
    sheets.push({ frame, sheet, viewport, canvas });
  }
  const resize = () =>
    sheets.forEach(({ frame, sheet, viewport, canvas }) => {
      if (sheet.hidden) return;
      const scale = Math.min(1, viewport.clientWidth / frame.w);
      canvas.style.transform = `scale(${scale})`;
      viewport.style.height = `${frame.h * scale}px`;
    });
  document.querySelectorAll('[data-filter]').forEach((button) =>
    button.addEventListener('click', () => {
      document
        .querySelectorAll('[data-filter]')
        .forEach((other) => other.setAttribute('aria-pressed', String(other === button)));
      for (const { frame, sheet } of sheets) {
        const category = frame.id.startsWith('intro')
          ? 'intro'
          : frame.id.startsWith('map')
            ? 'map'
            : frame.id.startsWith('mobile')
              ? 'mobile'
              : 'notes';
        sheet.hidden = button.dataset.filter !== 'all' && button.dataset.filter !== category;
      }
      resize();
    }),
  );
  window.addEventListener('resize', resize);
  resize();
})();

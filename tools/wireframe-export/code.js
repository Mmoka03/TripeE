// 가져오기는 새 화면을 추가하고, 글꼴 맞추기는 일치하는 기존 화면의 글꼴만 변경함.
figma.showUI(__html__, { width: 420, height: 420 });
let running = false;
figma.ui.onmessage = async (message) => {
  if (!['import', 'update-fonts'].includes(message.type) || running) return;
  running = true;
  const created = [];
  try {
    const { frames, images } = message.data;
    const fonts = (await figma.listAvailableFontsAsync()).map((item) => item.fontName);
    const family = ['Pretendard Variable', 'Pretendard', 'Noto Sans KR'].find((name) =>
      fonts.some((font) => font.family === name),
    );
    if (!family)
      throw new Error('Pretendard 또는 Noto Sans KR 글꼴을 설치한 뒤 다시 실행해 주세요.');
    const familyFonts = fonts.filter((font) => font.family === family);
    const findStyle = (style) =>
      familyFonts.find((font) => font.style.replace(/[\s-]/g, '').toLowerCase() === style);
    const regular = findStyle('regular') || familyFonts[0];
    const medium = findStyle('medium') || regular;
    const semibold = findStyle('semibold') || medium;
    const bold = findStyle('bold') || semibold;
    await Promise.all([regular, medium, semibold, bold].map((font) => figma.loadFontAsync(font)));
    if (message.type === 'update-fonts') {
      if (!family.startsWith('Pretendard'))
        throw new Error('Pretendard 설치 후 Figma를 다시 열고 실행해 주세요.');
      const plans = [];
      const wrappers = [];
      for (const source of frames) {
        const matches = figma.currentPage.children.filter(
          (node) => node.type === 'FRAME' && node.name === `${source.title} · 2026-11-13`,
        );
        if (matches.length !== 1)
          throw new Error(`기존 화면을 하나로 식별할 수 없어 중단함: ${source.title}`);
        const wrapper = matches[0];
        const [title, note, screen] = wrapper.children;
        if (
          title?.type !== 'TEXT' ||
          title.characters !== source.title ||
          note?.type !== 'TEXT' ||
          note.characters !== source.notes ||
          screen?.type !== 'FRAME' ||
          screen.children.length !== source.nodes.length
        )
          throw new Error(`원본과 구조가 달라 중단함: ${source.title}`);
        wrappers.push(wrapper);
        plans.push({ node: title, font: bold }, { node: note, font: regular });
        source.nodes.forEach((item, index) => {
          if (item.type !== 'text') return;
          const node = screen.children[index];
          if (node.type !== 'TEXT' || node.name !== item.name || node.characters !== item.text)
            throw new Error(`원본과 문구가 달라 중단함: ${source.title} / ${item.name}`);
          plans.push({
            node,
            font:
              item.weight >= 700
                ? bold
                : item.weight >= 600
                  ? semibold
                  : item.weight >= 500
                    ? medium
                    : regular,
          });
        });
      }
      // 기존 글꼴도 먼저 불러와 실패 시 원래 상태로 되돌릴 수 있게 함.
      const oldFonts = new Map();
      for (const plan of plans) {
        if (plan.node.fontName === figma.mixed)
          throw new Error(`혼합 글꼴은 자동 변경하지 않음: ${plan.node.name}`);
        plan.previous = plan.node.fontName;
        oldFonts.set(JSON.stringify(plan.previous), plan.previous);
      }
      await Promise.all([...oldFonts.values()].map((font) => figma.loadFontAsync(font)));
      try {
        for (const plan of plans) plan.node.fontName = plan.font;
        if (plans.some(({ node }) => node.hasMissingFont || node.fontName.family !== family))
          throw new Error('일부 글꼴을 적용하지 못해 기존 글꼴로 되돌림.');
      } catch (error) {
        for (const plan of plans) plan.node.fontName = plan.previous;
        throw error;
      }
      const clipped = plans.filter(
        ({ node }) =>
          node.parent.type === 'FRAME' &&
          node.parent.name.startsWith('화면 ') &&
          (node.x < -0.5 ||
            node.y < -0.5 ||
            node.x + node.width > node.parent.width + 0.5 ||
            node.y + node.height > node.parent.height + 0.5),
      );
      figma.ui.postMessage({
        type: 'done',
        text:
          `${wrappers.length}개 화면 · 텍스트 ${plans.length}개 글꼴 변경 완료\n${family} · 400 / 500 / 600 / 700\n화면 밖 텍스트 ${clipped.length}개 · 새 화면 추가 없음` +
          clipped
            .map(
              ({ node }) =>
                `\n${node.parent.parent.name} / ${node.name}: ${node.x},${node.y},${node.width}×${node.height}`,
            )
            .join(''),
      });
      return;
    }
    const rgb = (hex) => {
      if (hex === 'white') hex = '#ffffff';
      const value = parseInt(hex.slice(1), 16);
      return { r: (value >> 16) / 255, g: ((value >> 8) & 255) / 255, b: (value & 255) / 255 };
    };
    const paint = (hex) => [{ type: 'SOLID', color: rgb(hex) }];
    const hashes = {};
    for (const [src, bytes] of Object.entries(images))
      hashes[src] = figma.createImage(new Uint8Array(bytes)).hash;
    const startX =
      Math.max(0, ...figma.currentPage.children.map((node) => node.x + node.width)) + 160;
    for (let index = 0; index < frames.length; index++) {
      const source = frames[index];
      const wrapper = figma.createFrame();
      created.push(wrapper);
      wrapper.name = `${source.title} · 2026-11-13`;
      wrapper.fills = [];
      wrapper.resize(source.w, source.h + 220);
      wrapper.x = startX + (index % 4) * 2060;
      wrapper.y = Math.floor(index / 4) * 1420;
      wrapper.layoutMode = 'VERTICAL';
      wrapper.primaryAxisSizingMode = 'AUTO';
      wrapper.counterAxisSizingMode = 'FIXED';
      wrapper.itemSpacing = 18;
      const title = figma.createText();
      title.fontName = bold;
      title.fontSize = source.w < 700 ? 23 : 36;
      title.characters = source.title;
      title.fills = paint('#182031');
      wrapper.appendChild(title);
      title.resize(source.w, 60);
      title.textAutoResize = 'HEIGHT';
      const note = figma.createText();
      note.fontName = regular;
      note.fontSize = source.w < 700 ? 14 : 22;
      note.characters = source.notes;
      note.fills = paint('#637376');
      wrapper.appendChild(note);
      note.resize(source.w, 80);
      note.textAutoResize = 'HEIGHT';
      const screen = figma.createFrame();
      screen.name = `화면 ${source.w}×${source.h}`;
      screen.resize(source.w, source.h);
      screen.fills = paint(source.bg);
      screen.clipsContent = true;
      wrapper.appendChild(screen);
      screen.layoutSizingHorizontal = 'FIXED';
      screen.layoutSizingVertical = 'FIXED';
      for (const item of source.nodes) {
        let node;
        if (item.type === 'text') {
          node = figma.createText();
          // 웹 와이어프레임의 500과 600 굵기를 서로 구분해 적용함.
          node.fontName =
            item.weight >= 700
              ? bold
              : item.weight >= 600
                ? semibold
                : item.weight >= 500
                  ? medium
                  : regular;
          node.fontSize = item.size;
          node.lineHeight = { unit: 'PERCENT', value: item.lineHeight * 100 };
          node.textAlignHorizontal = item.align === 'center' ? 'CENTER' : 'LEFT';
          node.characters = item.text;
          node.fills = paint(item.fill);
          node.resize(item.w, item.h);
          node.textAutoResize = 'HEIGHT';
        } else {
          node = figma.createRectangle();
          node.resize(item.w, item.h);
          node.cornerRadius = item.radius || 0;
          node.fills =
            item.type === 'image'
              ? [{ type: 'IMAGE', imageHash: hashes[item.src], scaleMode: 'FILL' }]
              : paint(item.fill);
        }
        screen.appendChild(node);
        node.name = item.name;
        node.x = item.x;
        node.y = item.y;
        node.opacity = item.opacity ?? 1;
      }
      figma.ui.postMessage({
        type: 'progress',
        text: `${index + 1} / ${frames.length} 화면 추가 중`,
      });
    }
    figma.currentPage.selection = created;
    figma.viewport.scrollAndZoomIntoView(created.slice(0, 4));
    figma.ui.postMessage({
      type: 'done',
      text: `${created.length}개 화면 추가 완료 · ${family}. 기존 화면은 유지했습니다.`,
    });
  } catch (error) {
    // 생성 도중 실패하면 이번 가져오기에서 만든 프레임만 정리함.
    for (const node of created) if (!node.removed) node.remove();
    figma.ui.postMessage({ type: 'error', text: error.message });
  } finally {
    running = false;
  }
};

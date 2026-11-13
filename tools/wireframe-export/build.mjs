import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

// 원본 사진을 중복 관리하지 않고 배포용 가져오기 파일에만 포함함.
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const target = process.argv[2];
if (!target)
  throw new Error('출력 폴더를 지정해야 함: node tools/wireframe-export/build.mjs <폴더>');
const context = vm.createContext({});
vm.runInContext(
  await readFile(path.join(root, 'frontend/public/wireframe/frames.js'), 'utf8'),
  context,
);
const packageData = { ...context.TripeEWireframes, images: {} };
const sources = new Set(
  packageData.frames.flatMap((frame) =>
    frame.nodes.filter((node) => node.type === 'image').map((node) => node.src),
  ),
);
for (const src of sources) {
  packageData.images[src] = (
    await readFile(path.resolve(root, 'frontend/public/wireframe', src))
  ).toString('base64');
}
await mkdir(target, { recursive: true });
vm.runInContext(
  await readFile(path.join(root, 'frontend/public/design/city-examples-data.js'), 'utf8'),
  context,
);
const photoCredits = context.TripeEIntro.cities
  .flatMap((city) => [city.photo, city.mood])
  .filter((photo) => sources.has(`../design/${photo.src}`))
  .map(
    (photo) =>
      `${photo.src}\n${photo.title} / ${photo.author}\n${photo.license}: ${photo.licenseUrl}\n출처: ${photo.source}\n화면 비율에 맞게 크롭함. 일부 화면에 어두운 오버레이를 적용함.\n`,
  );
photoCredits.push(
  "earth-night-2016.jpg\nBlack Marble 2016 (Rotating Globe) / NASA's Goddard Space Flight Center\n출처: https://svs.gsfc.nasa.gov/30878/\n이용 지침: https://www.nasa.gov/nasa-brand-center/images-and-media/\n2016년 관측자료, 화면 비율에 맞게 크롭함.\n",
);
await writeFile(path.join(target, 'PHOTO-CREDITS.txt'), photoCredits.join('\n'));
await copyFile(path.join(here, 'code.js'), path.join(target, 'code.js'));
const template = await readFile(path.join(here, 'ui.html'), 'utf8');
const marker = /(['"])__TRIPEE_WIREFRAME_DATA__\1/g;
if ([...template.matchAll(marker)].length !== 1)
  throw new Error('UI 데이터 삽입 위치가 없거나 중복됨. 패키지 생성을 중단함.');
// 포매터가 세미콜론·공백을 바꿔도 유지되는 문자열 표식을 사용함.
const ui = template.replace(marker, () => JSON.stringify(packageData).replaceAll('<', '\\u003c'));
await writeFile(path.join(target, 'ui.html'), ui);
await writeFile(
  path.join(target, 'manifest.template.json'),
  JSON.stringify(
    {
      name: 'TripeE 사용자 와이어프레임',
      api: '1.0.0',
      main: 'code.js',
      ui: 'ui.html',
      editorType: ['figma'],
      documentAccess: 'dynamic-page',
      networkAccess: { allowedDomains: ['none'] },
    },
    null,
    2,
  ),
);
await writeFile(
  path.join(target, 'README.txt'),
  '1. Figma 데스크톱에서 기존 디자인 파일을 연 뒤 Plugins > Development > New plugin에서 Figma design / Custom UI로 로컬 플러그인을 생성함.\n2. Figma가 생성한 폴더의 code.js와 ui.html을 이 패키지의 같은 이름 파일로 교체함. manifest.json의 발급된 id는 유지함. 추가 컴파일은 필요 없음.\n3. manifest.template.json은 설정 참고용임. 생성된 manifest의 main은 code.js, ui는 ui.html, documentAccess는 dynamic-page, networkAccess.allowedDomains는 ["none"]으로 맞춤.\n4. 생성한 플러그인을 실행하고 가져오기를 누르면 현재 페이지 오른쪽에 새 프레임이 추가됨. 기존 프레임은 변경하지 않음.\n\n텍스트와 도형은 편집 가능함. 사진만 이미지로 저장함. 정적 와이어프레임이며 실제 애니메이션은 웹에서 검수함.\nPretendard Variable을 우선 사용함. 없으면 Pretendard 또는 Noto Sans KR로 가져옴. 400·500·600·700 굵기를 각각 Regular·Medium·SemiBold·Bold로 적용함. 기존 화면은 「기존 21개 화면의 글꼴 맞추기」로 글꼴만 변경할 수 있으며 원본 구조와 문구가 다르면 중단함.\n2026-11-14 기존 사용자 와이어프레임 파일에 21개 화면을 가져온 뒤 공식 Pretendard 1.3.9를 설치했음. 기존 21개 화면의 텍스트 341개를 Pretendard Variable로 맞췄으며 글꼴 변경 시 새 화면은 추가하지 않았음. 웹 글꼴과 설치 글꼴의 버전은 1.309로 일치함.\n사진 출처와 이용 조건: PHOTO-CREDITS.txt\n공식 로컬 플러그인 안내: https://developers.figma.com/docs/plugins/plugin-quickstart-guide/\n',
);
console.log(JSON.stringify({ frames: packageData.frames.length, images: sources.size, target }));

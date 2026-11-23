import { defineConfig } from 'vite';

// 브라우저는 같은 출처의 /api를 호출하고 개발 서버가 백엔드로 전달함.
export default defineConfig({
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  preview: { proxy: { '/api': 'http://127.0.0.1:8080' } },
});

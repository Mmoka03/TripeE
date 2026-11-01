import React, { lazy, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import './layout.css';

// 관리자 진입 시 지도 번들을 함께 받지 않도록 화면을 분리함.
const App = lazy(() => import('./App'));
const AdminApp = lazy(() => import('./admin/AdminApp'));
const isAdmin = location.pathname === '/admin' || location.pathname.startsWith('/admin/');

createRoot(document.getElementById('root')).render(
  <Suspense fallback={<p role="status">화면을 불러오는 중이에요.</p>}>
    {isAdmin ? <AdminApp /> : <App />}
  </Suspense>,
);

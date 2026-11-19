import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server (and `vite preview`, used by the e2e tests) forwards /api to
// the Spring Boot backend, so the browser only ever talks to one origin and no
// CORS setup is needed. In production the CDN proxies /api the same way (P-16).
// API_PROXY_TARGET lets the e2e tests point `vite preview` at their own backend.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const apiProxy = { '/api': env.API_PROXY_TARGET || 'http://localhost:8080' };
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: apiProxy,
    },
    preview: {
      port: 4173,
      proxy: apiProxy,
    },
  };
});

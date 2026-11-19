import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server forwards /api to the Spring Boot backend, so the browser only
// ever talks to one origin and no CORS setup is needed. In production the CDN
// proxies /api the same way (P-16).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});

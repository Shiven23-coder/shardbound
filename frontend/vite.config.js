import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server forwards /api to the Javalin backend, so the browser only
// ever talks to one origin and no CORS setup is needed (decision S5).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:7070',
    },
  },
});

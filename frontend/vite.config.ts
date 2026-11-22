import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server (and `vite preview`, used by the e2e tests) forwards /api to
// the Spring Boot backend, so the browser only ever talks to one origin and no
// CORS setup is needed. In production the CDN proxies /api the same way (P-16).
// API_PROXY_TARGET lets the e2e tests point `vite preview` at their own backend.
// Maps and art come straight from ../content and are hashed into the build (P-35).
// `phaser` is Shardbound's own Phaser build (src/world/phaserBuild.cjs, P-60),
// compiled from Phaser's source with the flags Phaser's webpack build sets: both
// renderers (Canvas stays as the fallback), no debug, no Phaser sound.
const fromHere = (relative: string) =>
  decodeURIComponent(new URL(relative, import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1');   // "/S:/x" on Windows
const phaserSource = fromHere('./node_modules/phaser/src/');
const phaserBuild = fromHere('./src/world/phaserBuild.cjs');
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const apiProxy = { '/api': env.API_PROXY_TARGET || 'http://localhost:8080' };
  return {
    plugins: [react()],
    resolve: {
      alias: [
        { find: /^phaser$/, replacement: phaserBuild },
        { find: /^phaser-src\//, replacement: phaserSource },
      ],
    },
    define: {
      'typeof CANVAS_RENDERER': JSON.stringify(true),
      'typeof WEBGL_RENDERER': JSON.stringify(true),
      'typeof WEBGL_DEBUG': JSON.stringify(false),
      'typeof FEATURE_SOUND': JSON.stringify(false),
      'typeof PLUGIN_CAMERA3D': JSON.stringify(false),
      'typeof PLUGIN_FBINSTANT': JSON.stringify(false),
    },
    server: {
      port: 5173,
      proxy: apiProxy,
      fs: { allow: ['..'] },
    },
    // The Phaser chunk is loaded lazily and budgeted by its gzip size (§4.13), not this raw-size warning.
    build: { chunkSizeWarningLimit: 1600 },
    preview: {
      port: 4173,
      proxy: apiProxy,
    },
  };
});

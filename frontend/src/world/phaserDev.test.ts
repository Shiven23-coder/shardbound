import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createServer, type ViteDevServer } from 'vite';

// The dev server serves source files as they are, and phaserBuild.cjs is CommonJS:
// unless Vite pre-bundles it, the browser gets `require(...)` and no default export,
// and the world and battle scenes never start (`npm run dev` shows a black screen).
// The e2e tests run the production build, so only this test sees the dev server.

let server: ViteDevServer;
let origin: string;

beforeAll(async () => {
  server = await createServer({
    configFile: new URL('../../vite.config.ts', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'),
    cacheDir: 'node_modules/.vite-dev-test',
    logLevel: 'silent',
    server: { port: 0, strictPort: false, hmr: false, watch: null },
  });
  await server.listen();
  const address = server.httpServer!.address() as { port: number };
  origin = `http://localhost:${address.port}`;
}, 60_000);

afterAll(async () => {
  await server?.close();
});

describe('the dev server', () => {
  it('serves the Phaser build as a module with a default export', async () => {
    const scene = await (await fetch(`${origin}/src/world/startWorld.ts`)).text();
    const phaserUrl = /import\s+\w+\s+from\s+["']([^"']+)["']/.exec(
      scene.split('\n').find((line) => /phaser/i.test(line) && line.startsWith('import'))!,
    )![1]!;
    const phaser = await (await fetch(new URL(phaserUrl, origin))).text();
    expect(phaser).not.toMatch(/^require\(/m);
    expect(phaser).toMatch(/export\s*(default\b|\{[^}]*\bdefault\b)/);
    // Phaser's build flags apply here too: left as `typeof X`, both renderers drop out.
    expect(phaser).not.toMatch(/typeof (CANVAS_RENDERER|WEBGL_RENDERER|WEBGL_DEBUG|FEATURE_SOUND)\b/);
  }, 60_000);
});

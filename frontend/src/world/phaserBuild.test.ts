import { afterEach, describe, expect, it, vi } from 'vitest';

// Our own Phaser build (P-60, CLAUDE.md rule 13) has only the modules it lists:
// a Phaser path, game object factory or creator, camera effect, tween or timer
// method the app uses but the build leaves out is undefined at runtime and
// crashes the scene. Every one the app's code names must resolve (P-67).

/** The app's code with its comments taken out, so only what runs is scanned. */
const sources = Object.values(import.meta.glob<string>(
  ['../**/*.{ts,tsx}', '!../**/*.test.{ts,tsx}', '!../**/*.gen.ts'],
  { query: '?raw', import: 'default', eager: true },
)).map((source) => source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1'));

/** The names called as `<receiver>.<name>(`: the game object factories, the camera's methods and so on. */
function called(receiver: RegExp): string[] {
  const found = new Set<string>();
  const pattern = new RegExp(String.raw`${receiver.source}\.(\w+)\(`, 'g');
  for (const source of sources) {
    for (const match of source.matchAll(pattern)) found.add(match[1]!);
  }
  return [...found].sort();
}

/** The names `prototype` (with its prototype chain) does not have. */
function missingFrom(prototype: unknown, names: string[]): string[] {
  return names.filter((name) => typeof (prototype as Record<string, unknown>)[name] !== 'function');
}

/** `Phaser.A.B.C` paths in the source, without the type-only namespace. */
function phaserPaths(): string[] {
  const found = new Set<string>();
  for (const source of sources) {
    for (const match of source.matchAll(/\bPhaser\.([A-Z]\w*(?:\.\w+)*)/g)) {
      if (!match[1]!.startsWith('Types.')) found.add(match[1]!);
    }
  }
  return [...found].sort();
}

/** The two Node modules the loader needs; the frontend has no Node types. */
interface NodeModules {
  'node:module': {
    createRequire: (from: string) => (id: string) => unknown;
    Module: { _resolveFilename: (request: string, ...rest: unknown[]) => string };
  };
  'node:url': { fileURLToPath: (url: URL) => string };
}
const node = <K extends keyof NodeModules>(id: K): NodeModules[K] =>
  (globalThis as unknown as { process: { getBuiltinModule: (name: string) => unknown } }).process
    .getBuiltinModule(id) as NodeModules[K];

/** Loads phaserBuild.cjs in Node, resolving `phaser-src/` as vite.config.ts does. */
function loadBuild(): Record<string, unknown> {
  const { createRequire, Module } = node('node:module');
  const phaserSource = node('node:url').fileURLToPath(new URL('../../node_modules/phaser/src/', import.meta.url));
  // The WebGL debugger is required only when WEBGL_DEBUG is defined, which the build's flags rule out.
  const redirect = (request: string) => (request === 'phaser3spectorjs' ? `${phaserSource}utils/NOOP` : request);
  const resolve = Module._resolveFilename;
  Module._resolveFilename = (request, ...rest) =>
    resolve(request.startsWith('phaser-src/') ? phaserSource + request.slice('phaser-src/'.length) : redirect(request), ...rest);
  // Phaser's device checks run on load: a bare browser surface is enough for them.
  const context = new Proxy({}, {
    get: (_, key) => (key === 'getImageData' ? () => ({ data: new Uint8ClampedArray(16) }) : () => {}),
    set: () => true,
  });
  const element = () => ({ getContext: () => context, style: {}, setAttribute: () => {}, appendChild: () => {} });
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('Image', class { src = ''; });
  vi.stubGlobal('HTMLCanvasElement', class {});
  vi.stubGlobal('document', { createElement: element, documentElement: { style: {} }, addEventListener: () => {} });
  try {
    return createRequire(import.meta.url)('./phaserBuild.cjs') as Record<string, unknown>;
  } finally {
    Module._resolveFilename = resolve;
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
});

// Loading Phaser's source in Node takes seconds on a busy machine: more than the default 5 s budget.
describe('the Phaser build', { timeout: 20_000 }, () => {
  it('has every Phaser path the app uses', () => {
    const paths = phaserPaths();
    expect(paths).toContain('Scenes.Events.SHUTDOWN');               // the scan sees the scenes
    const Phaser = loadBuild();
    const missing = paths.filter((path) =>
      path.split('.').reduce<unknown>((at, key) => (at as Record<string, unknown> | undefined)?.[key], Phaser) === undefined);
    expect(missing).toEqual([]);
  });

  it('has every game object factory and creator the scenes call', () => {
    const factories = called(/\b(?:this|scene)\.add/);
    const creators = called(/\b(?:this|scene)\.make/);
    expect(factories).toEqual(expect.arrayContaining(['sprite', 'particles']));   // the scan sees the scenes
    const Phaser = loadBuild() as unknown as {
      GameObjects: { GameObjectFactory: { prototype: unknown }; GameObjectCreator: { prototype: unknown } };
    };
    expect(missingFrom(Phaser.GameObjects.GameObjectFactory.prototype, factories)).toEqual([]);
    expect(missingFrom(Phaser.GameObjects.GameObjectCreator.prototype, creators)).toEqual([]);
  });

  it('has every camera effect and method, tween and timer method the scenes call', () => {
    const camera = called(/\b(?:camera|cameras\.main)/);
    expect(camera).toEqual(expect.arrayContaining(['fadeOut', 'flash', 'shake', 'pan', 'zoomTo']));
    const Phaser = loadBuild() as unknown as {
      Cameras: { Scene2D: { Camera: { prototype: unknown } } };
      Tweens: { TweenManager: { prototype: unknown } };
      Time: { Clock: { prototype: unknown } };
    };
    expect(missingFrom(Phaser.Cameras.Scene2D.Camera.prototype, camera)).toEqual([]);
    expect(missingFrom(Phaser.Tweens.TweenManager.prototype, called(/\bthis\.tweens/))).toEqual([]);
    expect(missingFrom(Phaser.Time.Clock.prototype, called(/\bthis\.time/))).toEqual([]);
  });
});

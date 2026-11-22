import { expect, test, type Locator, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Plays the world through the real UI: starts a game, walks the player tile
// by tile (P-51) with the keyboard (desktop) or the touch D-pad (phones) along
// a path over the map's collision layer, and uses objects with E or the
// interact button. It follows the tile the scene publishes on its canvas
// (data-x, data-y: the tile centre; data-facing, data-walking, data-area).

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;
const STEPS: Record<Dir, [number, number]> = { UP: [0, -1], DOWN: [0, 1], LEFT: [-1, 0], RIGHT: [1, 0] };
const KEYS: Record<Dir, string> = { UP: 'ArrowUp', DOWN: 'ArrowDown', LEFT: 'ArrowLeft', RIGHT: 'ArrowRight' };
const PAD: Record<Dir, string> = { UP: 'Walk up', DOWN: 'Walk down', LEFT: 'Walk left', RIGHT: 'Walk right' };
const balance = JSON.parse(readFileSync(path.join(ROOT, 'content', 'balance.json'), 'utf8'));
/** Milliseconds per one-tile step (P-51). */
export const STEP_MS: number = balance.world.stepMs;

export type Dir = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';
export type Controls = 'keyboard' | 'touch';

interface MapObject { name: string; type: string; x: number; y: number; w: number; h: number; props: Record<string, unknown> }
export interface WorldMap { width: number; height: number; blocked: boolean[]; walls: boolean[]; objects: MapObject[] }
interface Position { area: string; x: number; y: number }
/** Where the player is: the tile centre in pixels, the facing, and whether a step is under way. */
export interface Status extends Position { facing: Dir; walking: boolean }

const maps = new Map<string, WorldMap>();

/** An area's map: `walls` is the collision layer alone, `blocked` adds the NPC tiles (the walker steers round them). */
export function loadMap(area: string): WorldMap {
  const cached = maps.get(area);
  if (cached) return cached;
  const json = JSON.parse(readFileSync(path.join(ROOT, 'content', 'maps', `${area}.tmj`), 'utf8'));
  const collision = json.layers.find((l: { name: string }) => l.name === 'collision');
  const objects: MapObject[] = json.layers.find((l: { type: string }) => l.type === 'objectgroup').objects
    .map((o: { name: string; type: string; x: number; y: number; width: number; height: number; properties?: { name: string; value: unknown }[] }) => ({
      name: o.name, type: o.type, x: o.x / TILE, y: o.y / TILE, w: o.width / TILE, h: o.height / TILE,
      props: Object.fromEntries((o.properties ?? []).map((p) => [p.name, p.value])),
    }));
  const walls: boolean[] = collision.data.map((gid: number) => gid !== 0);
  const blocked = [...walls];
  for (const actor of objects.filter((o) => o.type === 'npc' || o.type === 'creep_spawn')) blocked[actor.y * json.width + actor.x] = true;
  const map = { width: json.width, height: json.height, blocked, walls, objects };
  maps.set(area, map);
  return map;
}

/** Taps on the phone projects and clicks elsewhere, so a phone stays in its touch layout (P-40). */
export async function press(locator: Locator): Promise<void> {
  if (test.info().project.use.hasTouch) await locator.tap();
  else await locator.click();
}

/** Starts a new game with a fresh profile and waits for the opening dialogue. */
export async function newGame(page: Page, username: string): Promise<void> {
  await page.goto('/');
  await press(page.getByRole('button', { name: 'New game' }));
  await page.getByLabel('Username').fill(username);
  await press(page.getByRole('button', { name: 'Start' }));
  await expect(page.getByRole('heading', { name: 'Storm Night' })).toBeVisible();
}

/** Pages through the open dialogue until the choice shows (P-44), then takes it. */
export async function choose(page: Page, label: string): Promise<void> {
  const choice = page.locator('.dialogue-box').getByRole('button', { name: label });
  await expect(page.locator('.dialogue-page')).toBeVisible();
  for (let turns = 0; turns < 60 && !(await choice.isVisible()); turns++) {
    await turnPage(page);
  }
  await expect(choice).toBeEnabled();
  await press(choice);
}

/** Completes or turns the dialogue page with a click or tap on its text, which never takes a choice. */
export async function turnPage(page: Page): Promise<void> {
  await press(page.locator('.dialogue-page'));
  await page.waitForTimeout(60);
}

export async function position(page: Page): Promise<Position | null> {
  return page.evaluate(() => {
    const canvas = document.querySelector('[data-testid="world-canvas"] canvas') as HTMLCanvasElement | null;
    if (!canvas?.dataset.area) return null;
    return { area: canvas.dataset.area, x: Number(canvas.dataset.x), y: Number(canvas.dataset.y) };
  });
}

/** The obstacles blocking now, as the scene publishes them (P-45). */
export async function activeObstacles(page: Page): Promise<string[]> {
  const names = await page.locator('[data-testid="world-canvas"] canvas').getAttribute('data-obstacles');
  return names ? names.split(',') : [];
}

/**
 * The map with every creep's aggro zone blocked too, except `target`'s: a walk
 * to somewhere else never starts a battle by accident (V2_SPEC §4.2).
 */
function avoidingCreeps(map: WorldMap, target: MapObject): WorldMap {
  const blocked = [...map.blocked];
  for (const creep of map.objects.filter((o) => o.type === 'creep_spawn' && o !== target)) {
    const radius = Number(creep.props.aggroRadius);
    for (let y = creep.y - radius; y <= creep.y + radius; y++) {
      for (let x = creep.x - radius; x <= creep.x + radius; x++) {
        if (x >= 0 && y >= 0 && x < map.width && y < map.height) blocked[y * map.width + x] = true;
      }
    }
  }
  return { ...map, blocked };
}

/** A battle or a dialogue took over the screen. */
export async function eventOpen(page: Page): Promise<boolean> {
  return (await page.locator('.dialogue-box').isVisible()) || (await page.locator('.battle-box').isVisible());
}

/** The map with these obstacles' rectangles blocked (and walled) as well. */
export function withObstacles(map: WorldMap, active: string[]): WorldMap {
  const blocked = [...map.blocked];
  const walls = [...map.walls];
  for (const o of map.objects.filter((ob) => ob.type === 'obstacle' && active.includes(ob.name))) {
    for (let y = o.y; y < o.y + o.h; y++) {
      for (let x = o.x; x < o.x + o.w; x++) {
        blocked[y * map.width + x] = true;
        walls[y * map.width + x] = true;
      }
    }
  }
  return { ...map, blocked, walls };
}

export async function status(page: Page): Promise<Status | null> {
  return page.evaluate(() => {
    const canvas = document.querySelector('[data-testid="world-canvas"] canvas') as HTMLCanvasElement | null;
    if (!canvas?.dataset.area) return null;
    const d = canvas.dataset;
    return { area: d.area!, x: Number(d.x), y: Number(d.y), facing: d.facing as Dir, walking: d.walking === '1' };
  });
}

/** The tile under a status or position. */
export function tileOf(p: Position): [number, number] {
  return [Math.floor(p.x / TILE), Math.floor(p.y / TILE)];
}

async function waitForPosition(page: Page): Promise<Position> {
  await expect.poll(() => position(page), { timeout: 20_000 }).not.toBeNull();
  return (await position(page))!;
}

/**
 * Taps the directions in order, in one burst inside the page: key-down and
 * key-up on the window (keyboard), or pointer-down and -up on the D-pad
 * buttons (touch), all within one task, so no frame ever sees a direction
 * held. Each tap is one queued step, or a turn when standing still facing
 * elsewhere (P-51).
 */
export async function taps(page: Page, controls: Controls, dirs: Dir[]): Promise<void> {
  await page.evaluate(({ controls, presses }) => {
    for (const { keyName, keyCode, padLabel } of presses) {
      const pad = [...document.querySelectorAll('button')].find((b) => b.getAttribute('aria-label') === padLabel);
      for (const down of [true, false]) {
        if (controls === 'keyboard') {
          const event = new KeyboardEvent(down ? 'keydown' : 'keyup', { key: keyName, code: keyName, bubbles: true });
          Object.defineProperty(event, 'keyCode', { get: () => keyCode });
          window.dispatchEvent(event);
        } else {
          pad?.dispatchEvent(new PointerEvent(down ? 'pointerdown' : 'pointerup',
            { bubbles: true, pointerType: 'touch', pointerId: 7, isPrimary: true }));
        }
      }
    }
  }, {
    controls,
    presses: dirs.map((dir) => ({ keyName: KEYS[dir], keyCode: { UP: 38, DOWN: 40, LEFT: 37, RIGHT: 39 }[dir], padLabel: PAD[dir] })),
  });
}

/** Taps one direction `count` times in one burst (see {@link taps}). */
export async function tap(page: Page, controls: Controls, dir: Dir, count: number): Promise<void> {
  await taps(page, controls, Array<Dir>(count).fill(dir));
}

/** How long the player must stand on one tile to count as settled: longer than a turn and a step, with frames to spare. */
const SETTLE_MS = 1000;

/**
 * Waits inside the page until the player stands on `goal` (if given) or has
 * stood still on one tile for SETTLE_MS, or the area changes, or a dialogue
 * opens. Returns where the player is and whether something interrupted.
 */
export async function settle(page: Page, area: string, goal?: [number, number], timeoutMs = 30_000): Promise<Status & { interrupted: boolean }> {
  return page.evaluate(async ({ area, goal, timeoutMs, settleMs, tile }) => {
    const canvas = document.querySelector('[data-testid="world-canvas"] canvas') as HTMLCanvasElement;
    const read = () => ({
      area: canvas.dataset.area ?? '', x: Number(canvas.dataset.x), y: Number(canvas.dataset.y),
      facing: canvas.dataset.facing as 'UP' | 'DOWN' | 'LEFT' | 'RIGHT', walking: canvas.dataset.walking === '1',
    });
    const interrupted = () => read().area !== area || document.querySelector('.dialogue-box, .battle-box') !== null;
    const started = performance.now();
    let still = { key: '', since: started };
    await new Promise<void>((resolve) => {
      const frame = () => {
        const p = read();
        const now = performance.now();
        const key = `${p.x},${p.y},${p.walking}`;
        if (key !== still.key) still = { key, since: now };
        const onGoal = goal !== null && !p.walking && Math.floor(p.x / tile) === goal[0] && Math.floor(p.y / tile) === goal[1];
        if (onGoal || interrupted() || (!p.walking && now - still.since >= settleMs) || now - started > timeoutMs) resolve();
        else requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    });
    return { ...read(), interrupted: interrupted() };
  }, { area, goal: goal ?? null, timeoutMs, settleMs: SETTLE_MS, tile: TILE });
}

const TURN_COST = 4;
const WALL_COST = 3;

/**
 * The cheapest 4-way path of tiles from `start` to any tile that satisfies
 * `goal`: each step costs 1, each turn and each step beside a wall cost more,
 * so the walk keeps to open ground in long straight runs (Dijkstra over
 * tile and direction).
 */
function findPath(map: WorldMap, start: [number, number], goal: (x: number, y: number) => boolean): [number, number][] {
  const free = (x: number, y: number) => x >= 0 && y >= 0 && x < map.width && y < map.height && !map.blocked[y * map.width + x];
  const besideWall = (x: number, y: number) => Object.values(STEPS).some(([dx, dy]) => !free(x + dx, y + dy));
  const dirs = Object.keys(STEPS) as Dir[];
  type Node = { x: number; y: number; dir: number; cost: number; previous: Node | null };
  const best = new Map<string, number>();
  const open: Node[] = [{ x: start[0], y: start[1], dir: -1, cost: 0, previous: null }];
  while (open.length > 0) {
    open.sort((a, b) => a.cost - b.cost);
    const node = open.shift()!;
    if (goal(node.x, node.y)) {
      const path: [number, number][] = [];
      for (let n: Node | null = node; n; n = n.previous) path.unshift([n.x, n.y]);
      return path;
    }
    dirs.forEach((dir, d) => {
      const [dx, dy] = STEPS[dir];
      const nx = node.x + dx;
      const ny = node.y + dy;
      if (!free(nx, ny)) return;
      const cost = node.cost + 1 + (node.dir !== -1 && node.dir !== d ? TURN_COST : 0) + (besideWall(nx, ny) ? WALL_COST : 0);
      const key = `${nx},${ny},${d}`;
      if (cost >= (best.get(key) ?? Infinity)) return;
      best.set(key, cost);
      open.push({ x: nx, y: ny, dir: d, cost, previous: node });
    });
  }
  throw new Error(`No path from ${start} in this map`);
}

/** Straight runs of a tile path: the direction and the tile each run ends on. */
function runs(tiles: [number, number][]): { dir: Dir; to: [number, number] }[] {
  const result: { dir: Dir; to: [number, number] }[] = [];
  for (let i = 1; i < tiles.length; i++) {
    const [dx, dy] = [tiles[i]![0] - tiles[i - 1]![0], tiles[i]![1] - tiles[i - 1]![1]];
    const dir = (Object.keys(STEPS) as Dir[]).find((d) => STEPS[d][0] === dx && STEPS[d][1] === dy)!;
    const last = result.at(-1);
    if (last && last.dir === dir) last.to = tiles[i]!;
    else result.push({ dir, to: tiles[i]! });
  }
  return result;
}

/**
 * Walks one straight run of `tiles` tiles in `dir`, one tap per tile (plus a
 * turn tap if the player faces elsewhere), and waits until the player stands
 * on `to`, stops short, or is interrupted. Returns false if interrupted: the
 * area changed (an exit) or a dialogue opened (a trigger).
 */
async function walkRun(page: Page, controls: Controls, area: string, dir: Dir, to: [number, number], tiles: number): Promise<boolean> {
  const here = await status(page);
  if (!here || here.area !== area) return false;                      // an exit already took the player on
  await tap(page, controls, dir, tiles + (here.facing === dir ? 0 : 1));
  const result = await settle(page, area, to, 10_000 + tiles * STEP_MS * 4);
  return !result.interrupted;
}

/**
 * Walks to an object of the current area: onto an exit or trigger (which the
 * scene then sends), or next to an NPC or object. After an exit it returns
 * once the next area's scene has started.
 */
export async function walkTo(page: Page, controls: Controls, objectName: string): Promise<void> {
  const start = await waitForPosition(page);
  await walkWithin(page, controls, start, objectName);
  const exit = loadMap(start.area).objects.find((o) => o.name === objectName)?.type === 'exit';
  if (exit) {
    await expect.poll(async () => (await position(page))?.area, { timeout: 20_000 }).not.toBe(start.area);
    await waitForPosition(page);
  }
}

async function walkWithin(page: Page, controls: Controls, start: Position, objectName: string): Promise<void> {
  const plain = withObstacles(loadMap(start.area), await activeObstacles(page));
  const target = plain.objects.find((o) => o.name === objectName);
  if (!target) throw new Error(`${objectName} is not in ${start.area}`);
  const map = avoidingCreeps(plain, target);
  const onTop = target.type === 'exit' || target.type === 'trigger';
  const covers = (x: number, y: number) => x >= target.x && x < target.x + target.w && y >= target.y && y < target.y + target.h;
  const near = (x: number, y: number) =>
    Math.max(Math.max(target.x - x, x - (target.x + target.w - 1), 0), Math.max(target.y - y, y - (target.y + target.h - 1), 0)) <= 1;
  const goal = (x: number, y: number) => (onTop ? covers(x, y) : near(x, y));
  const dialogueOpen = () => eventOpen(page);
  // Walk the path; if the player stopped short (an event in flight, a server snap-back), plan again from there.
  for (let attempt = 0; attempt < 3; attempt++) {
    // No position: the scene is loading the next area, so an exit took the player on.
    const here = await position(page);
    if (!here || here.area !== start.area || goal(...tileOf(here)) || (await dialogueOpen())) return;   // arrived, or an event took over
    let from = tileOf(here);
    for (const run of runs(findPath(map, from, goal))) {
      const tiles = Math.abs(run.to[0] - from[0]) + Math.abs(run.to[1] - from[1]);
      if (!(await walkRun(page, controls, start.area, run.dir, run.to, tiles))) return;   // an exit or a trigger took over
      const after = await position(page);
      if (!after || after.area !== start.area) return;                                 // an exit took over
      const now = tileOf(after);
      if (now[0] !== run.to[0] || now[1] !== run.to[1]) break;                            // stopped short: plan again
      from = run.to;
    }
  }
  const p = await position(page);
  if (p && p.area === start.area && !goal(...tileOf(p)) && !(await dialogueOpen())) {
    throw new Error(`Could not reach ${objectName}: at tile ${tileOf(p)} in ${p.area}`);
  }
}

/** Uses the NPC or object in reach (E on a keyboard, the interact button on touch). */
export async function interact(page: Page, controls: Controls): Promise<void> {
  if (controls === 'keyboard') await page.keyboard.press('e');
  else await page.getByRole('button', { name: 'Talk or use' }).dispatchEvent('pointerdown', { pointerType: 'touch' });
}

/** Waits until the world shows this area. */
export async function inArea(page: Page, areaName: string): Promise<void> {
  await expect(page.locator('.world-screen')).toHaveAttribute('data-area-name', areaName, { timeout: 20_000 });
}

/**
 * Wins the battle on screen through its menu (P-57): Attack, then Strike, every
 * round (a draught from the Bag below 40 % HP), then Continue once it has ended.
 * Returns the outcome the screen showed.
 */
export async function winBattle(page: Page): Promise<string> {
  const box = page.locator('.battle-box');
  await expect(box).toBeVisible();
  for (let step = 0; step < 300 && (await box.isVisible()); step++) {
    const continueButton = box.getByRole('button', { name: 'Continue' });
    if (await continueButton.isVisible()) {
      await press(continueButton);
      continue;
    }
    const menu = box.locator('.battle-menu');
    if (!(await menu.isVisible())) {
      await press(box.locator('.battle-text'));        // skip the round's text
      await page.waitForTimeout(80);
      continue;
    }
    const hp = Number(await page.locator('.battle-info-player [aria-label="HP"]').getAttribute('aria-valuenow'));
    const maxHp = Number(await page.locator('.battle-info-player [aria-label="HP"]').getAttribute('aria-valuemax'));
    const label = await menu.getAttribute('aria-label');
    if (label === 'Battle menu') {
      const bag = menu.getByRole('button', { name: /^Bag/ });
      await press(hp * 100 < maxHp * 40 && (await bag.isEnabled()) ? bag : menu.getByRole('button', { name: /^Attack/ }));
    } else if (label === 'attack') {
      await press(menu.getByRole('button', { name: /^Strike/ }));
    } else if (label === 'bag') {
      await press(menu.getByRole('button', { name: /^Healing Draught/ }));
    } else {
      await press(menu.getByRole('button', { name: /^Back/ }));
    }
    await page.waitForTimeout(120);
  }
  await expect(box).toBeHidden();
  return 'done';
}

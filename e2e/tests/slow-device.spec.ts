import { expect, test, type Page } from '@playwright/test';
import * as backend from '../support/backend';
import {
  activeObstacles, chests, choose, inArea, interact, loadMap, newGame, openBag, restAtLantern, settle, status, tap, taps, tileOf,
  toTheHerald, toTheLanternWithTheShard, toTheRuinsAtDawn, toTheRuinsLanternAfterTheAshFalls, walkTo, withObstacles,
  type Dir,
} from '../support/world';

// Tile steps (P-51) on a CPU slowed like a budget phone: the same taps reach
// the same tiles at 1×, 4× and 8× (no press lost to a long frame, no step
// added), a quick tap of E still talks, and pushing into a one-tile fence or
// Tanner's crates, straight or diagonally, never draws the player over a wall
// or an obstacle on any frame. The Herald's intro skips on a held E, and a
// boss round takes every press (P-67).

const TILE = 16;

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

async function throttle(page: Page, rate: number): Promise<void> {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate });
}

/** Records, on every animation frame, the area and where the player is drawn (data-dx, data-dy). */
async function startSampling(page: Page): Promise<void> {
  await page.evaluate(() => {
    const canvas = document.querySelector('[data-testid="world-canvas"] canvas') as HTMLCanvasElement;
    const w = window as unknown as { drawn: [string, number, number][] };
    w.drawn = [];
    const sample = () => {
      w.drawn.push([canvas.dataset.area ?? '', Number(canvas.dataset.dx), Number(canvas.dataset.dy)]);
      requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
}

/** Every frame sampled so far drew the player's tile-sized box over open ground only (walls and active obstacles). */
async function expectNeverOverlapped(page: Page, area: string): Promise<void> {
  const samples = await page.evaluate(() => (window as unknown as { drawn: [string, number, number][] }).drawn.splice(0));
  const map = withObstacles(loadMap(area), await activeObstacles(page));
  const inArea = samples.filter(([a]) => a === area);
  expect(inArea.length, 'frames sampled').toBeGreaterThan(10);
  for (const [, dx, dy] of inArea) {
    for (const y of [Math.floor((dy - TILE / 2) / TILE), Math.floor((dy + TILE / 2 - 1) / TILE)]) {
      for (const x of [Math.floor((dx - TILE / 2) / TILE), Math.floor((dx + TILE / 2 - 1) / TILE)]) {
        expect(map.walls[y * map.width + x], `drawn at ${dx},${dy} over tile ${x},${y} in ${area}`).toBe(false);
      }
    }
  }
}

/** One burst of taps, then the tile the player settles on. */
async function tapAndSettle(page: Page, area: string, dir: Dir, count: number): Promise<[number, number]> {
  await tap(page, 'keyboard', dir, count);
  const result = await settle(page, area);
  expect(result.interrupted, `walking ${dir} ×${count}`).toBe(false);
  return tileOf(result);
}

/** Holds a direction (or two at once) with real key events for `ms`. */
async function hold(page: Page, keys: string[], ms: number): Promise<void> {
  for (const key of keys) await page.keyboard.down(key);
  await page.waitForTimeout(ms);
  for (const key of keys) await page.keyboard.up(key);
}

// From home (7, 10) in Cottage Lane: the garden fence is the row above (tiles 8..11, 9), with an
// open gap at (7, 9) and a wall at (9, 8). A tap toward a new direction only turns the player.
const LANE_WALK: { dir: Dir; taps: number; arrives: [number, number] }[] = [
  { dir: 'LEFT', taps: 4, arrives: [5, 10] },     // two steps, then the wall at (4, 10)
  { dir: 'UP', taps: 3, arrives: [5, 10] },       // turn, then the wall at (5, 9)
  { dir: 'RIGHT', taps: 5, arrives: [9, 10] },    // turn, four steps
  { dir: 'UP', taps: 4, arrives: [9, 10] },       // turn, then the fence
  { dir: 'RIGHT', taps: 4, arrives: [12, 10] },   // turn, three steps
  { dir: 'UP', taps: 3, arrives: [12, 8] },       // turn, two steps through the gap past the fence's end
  { dir: 'LEFT', taps: 5, arrives: [10, 8] },     // turn, two steps, then the wall at (9, 8)
  { dir: 'DOWN', taps: 3, arrives: [10, 8] },     // turn, then the fence at (10, 9)
  { dir: 'RIGHT', taps: 3, arrives: [12, 8] },    // turn, two steps
  { dir: 'DOWN', taps: 3, arrives: [12, 10] },    // turn, two steps back round the fence's end
  { dir: 'LEFT', taps: 3, arrives: [10, 10] },    // turn, two steps: below the fence
];

for (const rate of [1, 4, 8]) {
  test(`at ${rate}× CPU the same taps reach the same tiles, and the fence holds`, async ({ page }) => {
    await newGame(page, `slow${rate}-${Date.now() % 1e9}`);
    await throttle(page, rate);
    await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
    await inArea(page, 'Cottage Lane');
    await expect.poll(async () => tileOf((await status(page))!)).toEqual([7, 10]);
    await startSampling(page);

    const arrivals: [number, number][] = [];
    for (const { dir, taps } of LANE_WALK) arrivals.push(await tapAndSettle(page, 'eldmere_cottage_lane', dir, taps));
    expect(arrivals).toEqual(LANE_WALK.map((w) => w.arrives));

    // Hold up into the fence from below it: the player turns and stays on (10, 10).
    await hold(page, ['ArrowUp'], 2000);
    expect(tileOf(await settle(page, 'eldmere_cottage_lane'))).toEqual([10, 10]);
    await expectNeverOverlapped(page, 'eldmere_cottage_lane');
  });
}

test("on a slow device a quick tap of E talks, and Tanner's crates hold from below and diagonally", async ({ page }) => {
  await newGame(page, `crates-${Date.now() % 1e9}`);
  await throttle(page, 4);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, 'keyboard', 'to_ruins');
  await inArea(page, 'The Old Ruins');
  await walkTo(page, 'keyboard', 'crater_crystal');
  await interact(page, 'keyboard');                                   // a single quick tap of E
  await expect(page.getByRole('heading', { name: 'The Ruins' })).toBeVisible();
  await choose(page, 'Take the crystal in your bare hands');
  await choose(page, 'Walk on');
  await walkTo(page, 'keyboard', 'to_lane');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, 'keyboard', 'to_square');
  await inArea(page, 'Eldmere Square');
  await choose(page, 'Look around the square');

  // Tanner's crates (27..28, 7) close the alley until the player follows Corvin; the alley mouth
  // below them (27..28, 8..9) is open, its trigger waiting for Corvin.
  const square = 'eldmere_square';
  await startSampling(page);
  await walkTo(page, 'keyboard', 'alley_mouth');
  const mouth = tileOf((await status(page))!);
  expect(mouth[0]).toBeGreaterThanOrEqual(27);
  expect(mouth[0]).toBeLessThanOrEqual(28);

  // Straight: taps, then a long hold, up into the crates.
  expect(await tapAndSettle(page, square, 'UP', 6)).toEqual([mouth[0], 8]);
  await hold(page, ['ArrowUp'], 2000);
  expect(tileOf(await settle(page, square))).toEqual([mouth[0], 8]);

  // Diagonally: back down and left, then a staircase of taps up and right into the crates (each tap
  // after the first turn steps at once, the player being on the move), then up and right held together.
  expect(await tapAndSettle(page, square, 'DOWN', 3)).toEqual([mouth[0], 10]);
  expect(await tapAndSettle(page, square, 'LEFT', 3)).toEqual([mouth[0] - 2, 10]);
  await taps(page, 'keyboard', ['UP', 'UP', 'RIGHT', 'UP', 'RIGHT', 'UP', 'RIGHT', 'UP']);
  expect(tileOf(await settle(page, square)), 'stopped by the crates').toEqual([mouth[0], 8]);
  await hold(page, ['ArrowUp', 'ArrowRight'], 2000);
  expect(tileOf(await settle(page, square)), 'along the crates to the alley wall').toEqual([28, 8]);
  await expectNeverOverlapped(page, square);
});

// A battle on a slow CPU (P-57): walking up to the Mire Slime starts it, and
// every key press lands: E opens Attack, E strikes (the menu gives way to the
// round's text), E skips the text, until the battle is won.
for (const rate of [4, 8]) {
  test(`at ${rate}× CPU a battle starts, takes every press and is won`, async ({ page }) => {
    await newGame(page, `slowb${rate}-${Date.now() % 1e9}`);
    await throttle(page, rate);
    await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
    await inArea(page, 'Cottage Lane');
    await walkTo(page, 'keyboard', 'meadow_creep_b');
    const box = page.locator('.battle-box');
    const menu = box.locator('.battle-menu');
    await expect(menu).toBeVisible({ timeout: 30_000 });
    await page.mouse.move(1, 1);                                      // no hover: the keys alone move the highlight

    for (let round = 0; round < 40 && (await box.isVisible()); round++) {
      if (await menu.isVisible()) {
        await page.keyboard.press('e');
        await expect(menu, 'E opened Attack').toHaveAttribute('aria-label', 'attack');
        await page.keyboard.press('e');
        await expect(menu, 'E struck: the round plays').toBeHidden();
      } else {
        await page.keyboard.press('e');                                // skip the round's text (or Continue once it ended)
        await page.waitForTimeout(150);
      }
    }
    await expect(box).toBeHidden();
  });
}

// Level-up presses on a slow CPU (P-61): every + lands, none waits on the server.
for (const rate of [4, 8]) {
  test(`at ${rate}× CPU every + on the level-up screen counts`, async ({ page }) => {
    await toTheLanternWithTheShard(page, 'keyboard', `slowl${rate}-${Date.now() % 1e9}`);
    await throttle(page, rate);
    await restAtLantern(page, 'keyboard');
    const box = page.locator('.lantern-box');
    await page.keyboard.press('e');                                  // Level up
    for (let i = 0; i < 5; i++) await page.keyboard.press('d');
    await expect(box.locator('.lantern-attribute').first()).toContainText('+5');
    await page.keyboard.press('s');                                  // Mind
    for (let i = 0; i < 3; i++) await page.keyboard.press('d');
    await expect(box.locator('.lantern-attribute').nth(1)).toContainText('+3');
    await expect(box.locator('.lantern-cost')).toBeVisible({ timeout: 15_000 });   // the preview catches up
  });
}

for (const rate of [4, 8]) {
  test(`at ${rate}× CPU the Bag takes every press: down, up, Compare, Equip (P-65)`, async ({ page }) => {
    await toTheRuinsAtDawn(page, 'keyboard', `slowb${rate}-${Date.now() % 1e9}`);
    await walkTo(page, 'keyboard', 'ruins_chest');
    await interact(page, 'keyboard');
    await expect.poll(() => chests(page)).toEqual({ ruins_chest: 'open' });
    await throttle(page, rate);
    await openBag(page, 'keyboard');
    const rows = page.locator('.item-list > li');
    for (let i = 0; i < 3; i++) await page.keyboard.press('s');
    await expect(rows.nth(3)).toHaveClass(/bag-highlighted/);
    for (let i = 0; i < 3; i++) await page.keyboard.press('w');
    await expect(rows.nth(0)).toHaveClass(/bag-highlighted/);
    await page.keyboard.press('e');
    await expect(page.locator('.weapon-compare')).toBeVisible();
    await page.keyboard.press('e');
    await expect(page.locator('.hud-weapon')).toContainText("Woodcutter's Hatchet", { timeout: 15_000 });
  });
}


// The Herald on a slow CPU (P-67, P-68): after the elder's path (played at
// full speed), a held E skips the intro at 4×, and a boss round takes every
// press: E opens Attack, E strikes and the round plays.
test('at 4× CPU a held E skips the intro, and a boss round takes every press', async ({ page }) => {
  test.setTimeout(480_000);
  await toTheRuinsLanternAfterTheAshFalls(page, 'keyboard', `slowh-${Date.now() % 1e9}`);
  await throttle(page, 4);
  await toTheHerald(page, 'keyboard');
  await expect(page.locator('.cutscene-line')).toBeVisible({ timeout: 30_000 });
  await page.keyboard.down('e');
  await page.waitForTimeout(1600);
  await page.keyboard.up('e');
  const box = page.locator('.battle-box');
  const menu = box.locator('.battle-menu');
  await expect(menu).toBeVisible({ timeout: 30_000 });
  await page.mouse.move(1, 1);
  await page.keyboard.press('e');
  await expect(menu, 'E opened Attack').toHaveAttribute('aria-label', 'attack');
  await page.keyboard.press('e');
  await expect(menu, 'E struck: the round plays').toBeHidden();
  await expect(page.locator('.boss-bar [aria-label="HP"]')).not.toHaveAttribute('aria-valuenow',
    await page.locator('.boss-bar [aria-label="HP"]').getAttribute('aria-valuemax') ?? '', { timeout: 30_000 });
});

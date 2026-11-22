import { expect, test } from '@playwright/test';
import path from 'node:path';
import * as backend from '../support/backend';
import { choose, inArea, interact, newGame, press, status, tap, turnPage, walkTo, type Controls } from '../support/world';

// Review screenshots of each layout (P-40): a dialogue page with its ▼
// marker, the choices with one highlighted, the player caught mid-step
// (P-51), calm exploration, and a gated obstacle (the flooded road) shown as
// a message box. Not a check; run on
// demand with SHOTS_DIR=<folder> npx playwright test -c e2e screenshots

const SHOTS_DIR = process.env.SHOTS_DIR;
test.skip(!SHOTS_DIR, 'screenshots are taken on demand (SHOTS_DIR=<folder>)');

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('dialogue pages and choices, calm exploration, a gated obstacle', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const shot = (name: string) => page.screenshot({ path: path.join(SHOTS_DIR!, `${testInfo.project.name}-${name}.png`) });
  const choicesShown = () => page.locator('.dialogue-body > .choice-list').isVisible();
  await newGame(page, `shots-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);

  await turnPage(page);                                              // completes the first page: ▼ shows
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible();
  await page.waitForTimeout(600);
  await shot('dialogue-page');

  for (let turns = 0; turns < 60 && !(await choicesShown()); turns++) await turnPage(page);
  if (controls === 'keyboard') {
    await page.mouse.move(1, 1);                                      // no hover: the keys alone move the highlight
    await page.keyboard.press('s');
  }
  await expect(page.locator('.choice-highlighted')).toBeVisible();
  await page.waitForTimeout(600);
  await shot('choices');

  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  // Turn first (taps are ignored while the arrival title still pauses the world), then three steps.
  await expect.poll(async () => {
    await tap(page, controls, 'RIGHT', 1);
    return (await status(page))?.facing;
  }, { timeout: 20_000 }).toBe('RIGHT');
  await tap(page, controls, 'RIGHT', 3);
  await page.waitForFunction(() => {
    const d = (document.querySelector('[data-testid="world-canvas"] canvas') as HTMLCanvasElement).dataset;
    return d.walking === '1' && Math.abs(Number(d.dx) - Number(d.x)) >= 5 && Math.abs(Number(d.dx) - Number(d.x)) <= 11;   // about half a tile
  }, undefined, { polling: 'raf' });
  await shot('walking');
  await walkTo(page, controls, 'flooded_road');
  for (const skip of await page.getByRole('button', { name: 'Skip this hint' }).all()) await press(skip);
  await expect(page.locator('.hud-cluster')).toHaveClass(/hud-faded/, { timeout: 10_000 });
  await expect(page.locator('.objective-line')).toBeHidden({ timeout: 10_000 });
  await page.waitForTimeout(800);
  await shot('calm');

  await interact(page, controls);
  await expect(page.getByRole('heading', { name: 'The Flooded Road' })).toBeVisible();
  const closeMarker = page.locator('.dialogue-close');                // a message: ■ instead of a choice
  for (let turns = 0; turns < 20 && !(await closeMarker.isVisible()); turns++) await turnPage(page);
  await page.waitForTimeout(800);   // the camera framing
  await shot('obstacle');
});

test('a battle: the menu, and a hit landing', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const shot = (name: string) => page.screenshot({ path: path.join(SHOTS_DIR!, `${testInfo.project.name}-${name}.png`) });
  await newGame(page, `shotb-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  for (let turns = 0; turns < 60 && !(await page.locator('.dialogue-body > .choice-list').isVisible()); turns++) await turnPage(page);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');

  await walkTo(page, controls, 'meadow_creep_b');
  const menu = page.locator('.battle-box .battle-menu');
  await expect(menu).toBeVisible({ timeout: 20_000 });
  if (controls === 'keyboard') await page.mouse.move(1, 1);
  await page.waitForTimeout(600);
  await shot('battle-menu');

  await press(menu.getByRole('button', { name: /^Attack/ }));
  await press(menu.getByRole('button', { name: /^Strike/ }));
  await page.waitForTimeout(450);                                     // the first hit's effect and number
  await shot('battle-hit');
  await expect(menu).toBeVisible({ timeout: 20_000 });
  await press(menu.getByRole('button', { name: /^Skills/ }));
  await page.waitForTimeout(300);
  await shot('battle-skills');
});

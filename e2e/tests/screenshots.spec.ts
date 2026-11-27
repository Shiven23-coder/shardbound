import { expect, test } from '@playwright/test';
import path from 'node:path';
import * as backend from '../support/backend';
import {
  chests, choose, fightTheHerald, holdSkip, inArea, interact, leaveLantern, newGame, openBag, press, readyForTheHerald, restAtLantern,
  status, tap, toTheRuinsAtDawn, turnPage, walkTo, winBattle, type Controls,
} from '../support/world';

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

test('the Lantern menu and the level-up screen', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const shot = (name: string) => page.screenshot({ path: path.join(SHOTS_DIR!, `${testInfo.project.name}-${name}.png`) });
  const meadow = async () => {
    for (const creep of ['meadow_creep_b', 'meadow_creep_a']) {
      await walkTo(page, controls, creep);
      await winBattle(page);
    }
  };
  await newGame(page, `shotl-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  for (let turns = 0; turns < 60 && !(await page.locator('.dialogue-body > .choice-list').isVisible()); turns++) await turnPage(page);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await meadow();                                                     // 70 Shards
  await walkTo(page, controls, 'to_ruins');
  await inArea(page, 'The Old Ruins');
  await walkTo(page, controls, 'crater_crystal');
  await interact(page, controls);
  await choose(page, 'Take the crystal in your bare hands');
  await choose(page, 'Walk on');
  await walkTo(page, controls, 'to_lane');
  await inArea(page, 'Cottage Lane');

  await restAtLantern(page, controls);                                // kindles it, then rests
  if (controls === 'keyboard') await page.mouse.move(1, 1);
  await page.waitForTimeout(500);
  await shot('lantern-menu');
  await leaveLantern(page);
  await page.waitForTimeout(600);
  await shot('lantern-on-map');                                       // lit, with Osric beside it

  await meadow();                                                     // the rest brought them back: 140 Shards
  await restAtLantern(page, controls);
  const box = page.locator('.lantern-box');
  await press(box.getByRole('button', { name: /^Level up/ }));
  await press(box.getByRole('button', { name: 'Raise Vigor' }));
  await expect(box.locator('.lantern-cost')).toContainText('100');
  if (controls === 'keyboard') await page.mouse.move(1, 1);
  await page.waitForTimeout(400);
  await shot('level-up');
});

test('a chest opening, its loot and Compare in the Bag (P-65)', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const shot = (name: string) => page.screenshot({ path: path.join(SHOTS_DIR!, `${testInfo.project.name}-${name}.png`) });
  await toTheRuinsAtDawn(page, controls, `shotc-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await walkTo(page, controls, 'ruins_chest');
  await expect(page.locator('.objective-line')).toBeHidden({ timeout: 10_000 });
  await page.waitForTimeout(600);
  await shot('chest-closed');
  await interact(page, controls);
  await expect.poll(() => chests(page), { intervals: [20] }).toEqual({ ruins_chest: 'open' });
  await page.waitForTimeout(120);                                     // the lid has popped; the sparks fly
  await shot('chest-opening');
  await expect(page.locator('.toast[data-kind="LOOT"]')).toBeVisible();
  await page.waitForTimeout(500);
  await shot('chest-loot');

  await openBag(page, controls);
  await page.waitForTimeout(300);
  await shot('bag');
  if (controls === 'keyboard') await page.keyboard.press('e');
  else await press(page.getByRole('button', { name: 'Compare' }));
  await expect(page.locator('.weapon-compare')).toBeVisible();
  await page.waitForTimeout(300);
  await shot('compare');
});

test('the Ashen Herald: the intro, the boss bar, phase two, its fall and the epilogue', async ({ page }, testInfo) => {
  test.setTimeout(600_000);
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const shot = (name: string) => page.screenshot({ path: path.join(SHOTS_DIR!, `${testInfo.project.name}-${name}.png`) });
  await newGame(page, `shoth-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  for (let turns = 0; turns < 60 && !(await page.locator('.dialogue-body > .choice-list').isVisible()); turns++) await turnPage(page);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await readyForTheHerald(page);
  await walkTo(page, controls, 'arena_way');
  await inArea(page, 'The Ash Rise');
  await expect(page.locator('.area-title')).toBeHidden({ timeout: 10_000 });
  await page.waitForTimeout(1500);
  await shot('herald-rise-before');                                   // the ash falling before the fight
  await walkTo(page, controls, 'herald_trigger');

  const line = page.locator('.cutscene-line');
  await expect(line).toBeVisible({ timeout: 30_000 });
  await page.waitForTimeout(2500);                                    // the first line typed out
  await shot('herald-intro');
  await holdSkip(page, controls, 550, () => shot('herald-intro-hold'));   // let go early: the ring, not a skip
  await holdSkip(page, controls, 1400);                                // held: skipped into the fight
  await expect(page.locator('.battle-box')).toBeVisible({ timeout: 20_000 });
  await page.waitForTimeout(700);
  await shot('herald-banner');

  const shots = new Set<string>();
  const once = async (name: string, wait: number) => {
    if (shots.has(name)) return;
    shots.add(name);
    if (controls === 'keyboard') await page.mouse.move(1, 1);
    await page.waitForTimeout(wait);
    await shot(name);
  };
  const won = await fightTheHerald(page, controls, 3, async () => {
    const menuOpen = await page.locator('.battle-menu').isVisible();
    const warned = await page.locator('.boss-bar-warning').isVisible();
    const phaseTwo = await page.locator('.boss-bar.boss-phase-2').isVisible();
    if (menuOpen && !warned && !phaseTwo) await once('herald-phase-one', 300);
    if (menuOpen && warned && !phaseTwo) await once('herald-telegraph', 300);
    if (menuOpen && warned && phaseTwo) await once('herald-phase-two-windup', 450);
    if (menuOpen && !warned && phaseTwo) await once('herald-phase-two-rest', 300);
    if (phaseTwo) await once('herald-phase-two', 350);
    if (await page.locator('.boss-banner-vanquished').isVisible()) await once('herald-vanquished', 500);
  });
  expect(won, 'the Herald fell (a loss leaves no epilogue to show)').toBe(true);

  await expect(line).toBeVisible({ timeout: 30_000 });
  await page.waitForTimeout(2500);
  await shot('herald-epilogue');
  for (let turns = 0; turns < 20 && (await line.isVisible()); turns++) {
    await press(line);
    await page.waitForTimeout(400);
  }
  await expect(page.locator('.hud-cluster')).toBeVisible({ timeout: 20_000 });
  await page.waitForTimeout(1500);
  await shot('herald-after');
});

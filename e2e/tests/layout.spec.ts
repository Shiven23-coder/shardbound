import { expect, test, type Page } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import { choose, inArea, newGame, press, turnPage } from '../support/world';

// The input-aware layouts (P-40) and the compact HUD (P-39), in each project:
// desktop 1280 × 800 with a keyboard, a phone upright at 390 × 844 and sideways
// at 844 × 390, both touch.

const LAYOUTS: Record<string, string> = { desktop: 'desktop', 'phone-portrait': 'portrait', 'phone-landscape': 'landscape' };
const DESKTOP_HUD_MAX_WIDTH = 256;   // .layout-desktop .hud-cluster { max-width: 16rem }

interface Box { x: number; y: number; width: number; height: number }

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

function overlaps(a: Box, b: Box): boolean {
  return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;
}

/** The player on screen with a tile of room around them (two above, for the head), in CSS pixels. */
async function playerSurroundings(page: Page): Promise<Box> {
  const canvas = page.locator('[data-testid="world-canvas"] canvas');
  await expect(canvas).toHaveAttribute('data-sx', /\d/);
  const { sx, sy, zoom } = await canvas.evaluate((c: HTMLCanvasElement) => ({
    sx: Number(c.dataset.sx), sy: Number(c.dataset.sy), zoom: Number(c.dataset.zoom),
  }));
  const view = (await page.locator('.world-view').boundingBox())!;
  const tile = 16 * zoom;
  return { x: view.x + sx - tile, y: view.y + sy - 2 * tile, width: 2 * tile, height: 3 * tile };
}

test('the layout follows the input in use, and the HUD stays small and off the player', async ({ page }, testInfo) => {
  const layout = LAYOUTS[testInfo.project.name]!;
  const touch = Boolean(testInfo.project.use.hasTouch);
  const viewport = page.viewportSize()!;
  await newGame(page, `layout-${layout.slice(0, 4)}-${Date.now() % 1e9}`);
  await expect(page.locator('.world-screen')).toHaveAttribute('data-layout', layout);

  // An open dialogue: inside the deck upright, over the bottom of the world otherwise.
  const dialogue = page.locator('.dialogue-box');
  await expect(dialogue).toBeVisible();
  // The world and what sits below it, read in the same frame: the portrait deck resizes with its content.
  const gapBelowWorld = (selector: string) => page.evaluate((below) => {
    const view = document.querySelector('.world-view')!.getBoundingClientRect();
    return document.querySelector(below)!.getBoundingClientRect().top - view.bottom;
  }, selector);
  const dialogueBox = (await dialogue.boundingBox())!;
  if (layout === 'portrait') {
    await expect(page.locator('.control-deck .dialogue-box')).toBeVisible();
    await expect(page.locator('.dpad')).toHaveCount(0);
    await expect.poll(() => gapBelowWorld('.control-deck'), { message: 'the dialogue sits below the world' }).toBeGreaterThanOrEqual(-1);
  } else {
    await expect(page.locator('.world-view .dialogue-box')).toBeVisible();
    if (layout === 'landscape') {
      expect(dialogueBox.height, 'the dialogue takes about a third of a short screen').toBeLessThanOrEqual(viewport.height * 0.36);
    }
  }
  if (touch) await expectTouchTargets(page, 'a dialogue');

  if (layout === 'desktop') {
    // Pages, then the keyboard picks (P-44): the highlight starts on the first choice, S moves it, 2 takes the second.
    await expect(page.locator('.dialogue-text').first()).toBeVisible();
    for (let turns = 0; turns < 60 && !(await page.locator('.dialogue-body > .choice-list').isVisible()); turns++) await turnPage(page);
    await page.locator('.dialogue-body > .choice-list .choice').first().hover();                  // the mouse moves the highlight too
    await expect(page.locator('.choice-highlighted')).toContainText('Help Corvin');
    await page.mouse.move(1, 1);
    await page.keyboard.press('s');
    await expect(page.locator('.choice-highlighted')).toContainText('Ignore the shouting');
    await page.keyboard.press('w');
    await expect(page.locator('.choice-highlighted')).toContainText('Help Corvin');
    await page.keyboard.press('2');
  } else {
    await choose(page, 'Ignore the shouting and follow the light');
  }
  await inArea(page, 'Cottage Lane');

  // The movement hint names the controls of this device.
  // Hints wait for the area title and the objective banner (P-49).
  await expect(page.locator('.hint-bubble')).toContainText(touch ? 'Hold the arrows' : 'WASD', { timeout: 15_000 });

  // The HUD cluster: small on phones, a fixed maximum width on desktop.
  const hud = (await page.locator('.hud-body').boundingBox())!;
  if (touch) {
    expect(hud.height, 'HUD height on a phone').toBeLessThanOrEqual(viewport.height * 0.1);
    expect(hud.width, 'HUD width on a phone').toBeLessThanOrEqual(viewport.width * 0.35);
  } else {
    expect((await page.locator('.hud-cluster').boundingBox())!.width, 'HUD width on desktop').toBeLessThanOrEqual(DESKTOP_HUD_MAX_WIDTH);
    await expect(page.locator('.hud-numbers')).toBeVisible();
  }
  const dodging = await page.locator('.hud-cluster').evaluate((el) => el.classList.contains('hud-dodge'));
  expect(overlaps(hud, await playerSurroundings(page)) && !dodging, 'the HUD covers the player').toBe(false);

  // Touch controls by layout.
  if (layout === 'desktop') {
    await expect(page.locator('.dpad, .interact-button, .control-deck, .touch-controls')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Controls' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Controls' })).toBeHidden();
  }
  if (layout === 'portrait') {
    const deck = page.locator('.control-deck');
    for (const name of ['Walk up', 'Walk left', 'Walk right', 'Walk down', 'Talk or use', 'Bag', 'Menu']) {
      await expect(deck.getByRole('button', { name, exact: true })).toBeVisible();
    }
    const deckBox = (await deck.boundingBox())!;
    await expect.poll(() => gapBelowWorld('.control-deck'), { message: 'the deck sits below the world' }).toBeGreaterThanOrEqual(-1);
    expect(deckBox.y + deckBox.height, 'the deck fits on screen').toBeLessThanOrEqual(viewport.height + 1);
  }
  if (layout === 'landscape') {
    const pad = page.locator('.touch-controls .dpad');
    const opacity = () => pad.evaluate((el) => Number(getComputedStyle(el).opacity));
    await expect.poll(opacity, { message: 'idle touch controls are translucent' }).toBeCloseTo(0.4, 1);
    const left = page.getByRole('button', { name: 'Walk left' });
    await left.dispatchEvent('pointerdown', { pointerType: 'touch' });
    await expect.poll(opacity, { message: 'pressed touch controls are opaque' }).toBe(1);
    await left.dispatchEvent('pointerup', { pointerType: 'touch' });
    await expect.poll(opacity).toBeCloseTo(0.4, 1);
  }
  if (touch) {
    await expectTouchTargets(page, 'the world');
    await press(page.locator('.hud-cluster'));
    await expect(page.locator('.hud-numbers'), 'a tap shows the numbers').toBeVisible();
  }
});

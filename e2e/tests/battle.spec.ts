import { expect, test, type Page } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import { choose, inArea, newGame, press, walkTo, winBattle, type Controls } from '../support/world';

// A meadow battle through the real UI in each layout (V4 acceptance, P-57):
// walking up to the Mire Slime starts it; the Skills and Bag pages open and
// go back by keyboard (1–4, Esc) or touch; every battle button is a thumb's
// size on a phone; the battle is won and the world comes back.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

/** Opens a page of the battle menu by its number (keyboard) or its button (touch). */
async function open(page: Page, controls: Controls, number: string, name: RegExp): Promise<void> {
  const menu = page.locator('.battle-box .battle-menu');
  if (controls === 'keyboard') await page.keyboard.press(number);
  else await press(menu.getByRole('button', { name }));
}

async function back(page: Page, controls: Controls): Promise<void> {
  if (controls === 'keyboard') await page.keyboard.press('Escape');
  else await press(page.locator('.battle-box .battle-menu').getByRole('button', { name: /^Back/ }));
  await expect(page.locator('.battle-box .battle-menu')).toHaveAttribute('aria-label', 'Battle menu');
}

test('a meadow battle: the menu pages, then a victory', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  await newGame(page, `e2e-battle-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon');
  await inArea(page, 'Cottage Lane');

  await walkTo(page, controls, 'meadow_creep_b');
  const box = page.locator('.battle-box');
  const menu = box.locator('.battle-menu');
  await expect(menu).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.battle-info-enemy')).toContainText('Mire Slime');
  if (controls === 'touch') await expectTouchTargets(page, 'the battle menu');

  await open(page, controls, '2', /^Skills/);
  await expect(menu).toHaveAttribute('aria-label', 'skills');
  await expect(menu.getByRole('button', { name: /^Rending Cut/ })).toBeVisible();
  await expect(menu.getByRole('button', { name: /^Crystal Pulse/ })).toBeVisible();
  if (controls === 'touch') await expectTouchTargets(page, 'the skills page');
  await back(page, controls);

  await open(page, controls, '3', /^Bag/);
  await expect(menu.getByRole('button', { name: /^Lantern Draught 3\/3/ })).toBeVisible();
  if (controls === 'touch') await expectTouchTargets(page, 'the bag page');
  await back(page, controls);

  await winBattle(page);
  await expect(page.locator('.world-screen')).not.toHaveClass(/in-battle/);
  await expect(page.getByTestId('world-canvas').locator('canvas')).toBeVisible();
});

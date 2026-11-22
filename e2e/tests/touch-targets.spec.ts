import { expect, test, type Locator } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import { choose, inArea, newGame, press } from '../support/world';

// Every touch target is at least 48 × 48 px on a phone, upright and sideways (§4.16).
// The fight screen is measured in the merchant-route test, which reaches it.

/** Waits until a dialog has finished scaling in, so its buttons are measured at their real size. */
async function settled(dialog: Locator): Promise<void> {
  await expect(dialog).toBeVisible();
  await expect.poll(() => dialog.evaluate((el) => getComputedStyle(el).transform)).toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
}

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('every screen keeps its touch targets at least 48 px', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'New game' })).toBeVisible();
  await expectTouchTargets(page, 'the title screen');
  for (const panel of ['Controls', 'Settings']) {
    await press(page.getByRole('button', { name: panel }));
    await settled(page.getByRole('dialog', { name: panel }));
    await expectTouchTargets(page, `the ${panel} screen`);
    await press(page.getByRole('dialog', { name: panel }).getByRole('button', { name: 'Close' }));
  }

  await press(page.getByRole('button', { name: 'New game' }));
  await expect(page.getByLabel('Username')).toBeVisible();
  await expectTouchTargets(page, 'the profile screen');

  await newGame(page, `touch-${Date.now() % 1e9}`);
  await expectTouchTargets(page, 'a dialogue');

  await choose(page, 'Ignore the shouting and follow the light');
  await inArea(page, 'Cottage Lane');
  await expect(page.locator('.hint-bubble')).toBeVisible({ timeout: 15_000 });   // after the title and the objective (P-49)
  await expectTouchTargets(page, 'the world with a hint');

  await press(page.getByRole('button', { name: 'Bag' }));
  await expect(page.getByRole('tablist', { name: 'Character panels' })).toBeVisible();
  for (const tab of ['Inventory', 'Quests', 'Reputation', 'Journal']) {
    await press(page.getByRole('tab', { name: tab }));
    await expectTouchTargets(page, `the ${tab} panel`);
  }
  await press(page.getByRole('tablist', { name: 'Character panels' }).getByRole('button', { name: 'Close' }));

  await press(page.getByRole('button', { name: 'Menu' }));
  await expectTouchTargets(page, 'the game menu');
  await press(page.getByRole('button', { name: 'Settings' }));
  await settled(page.getByRole('dialog', { name: 'Settings' }));
  await expectTouchTargets(page, 'the Settings screen in the game');
});

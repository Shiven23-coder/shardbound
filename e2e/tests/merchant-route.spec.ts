import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import { choose, inArea, interact, newGame, press, walkTo, type Controls } from '../support/world';

// The merchant route through the real UI, walking the world (V3 acceptance):
// keyboard on desktop, the touch D-pad and interact button on a phone held
// upright (the control deck) and sideways (the overlay). Every exit, trigger
// and interaction is validated by the server.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('the merchant route reaches its ending by walking the world', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  await newGame(page, `route-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');

  await walkTo(page, controls, 'to_ruins');
  await inArea(page, 'The Old Ruins');
  await walkTo(page, controls, 'crater_crystal');
  await interact(page, controls);
  await choose(page, 'Take the crystal in your bare hands');
  await choose(page, 'Walk on');

  await walkTo(page, controls, 'to_lane');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, controls, 'to_square');
  await inArea(page, 'Eldmere Square');
  await expect(page.getByRole('heading', { name: 'The Square at Dawn' })).toBeVisible();   // the trigger on arrival
  await choose(page, 'Look around the square');

  await walkTo(page, controls, 'corvin_stall');
  await interact(page, controls);
  await expect(page.getByRole('heading', { name: "Corvin's Offer" })).toBeVisible();
  await choose(page, 'Accept the three hundred gold');
  await choose(page, 'Follow him');

  await walkTo(page, controls, 'alley_mouth');
  await expect(page.getByRole('heading', { name: 'The Mouth of the Alley' })).toBeVisible();
  await choose(page, 'Step into the lantern light');
  const attack = page.locator('.combat-overlay').getByRole('button', { name: 'Attack', exact: true });
  await expect(attack).toBeVisible();
  if (controls === 'touch') await expectTouchTargets(page, 'a fight');
  while (await page.locator('.combat-overlay').isVisible()) {
    if (await attack.isEnabled()) await press(attack);
    await page.waitForTimeout(300);
  }

  await choose(page, 'Take the hatchet');
  await choose(page, 'Take the crystal back by force');
  await choose(page, 'Run for the forest road');
  await expect(page.getByRole('heading', { name: "The Merchant's Bargain" })).toBeVisible();
});

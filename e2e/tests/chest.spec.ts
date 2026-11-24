import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import { chests, inArea, interact, openBag, position, press, toTheRuinsAtDawn, walkTo, type Controls } from '../support/world';

// The gear slice through the real UI in each layout (V6a, P-65): the ruins chest
// opens once with the Woodcutter's Hatchet and a loot toast in its tier colour;
// the Bag compares it with the equipped sword by keyboard (desktop) or taps
// (phones) and equips it; walking pauses while the Bag is open.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('the ruins chest: opened once, compared and equipped', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  await toTheRuinsAtDawn(page, controls, `e2e-chest-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await expect.poll(() => chests(page)).toEqual({ ruins_chest: 'closed' });

  await walkTo(page, controls, 'ruins_chest');
  await interact(page, controls);
  await expect.poll(() => chests(page)).toEqual({ ruins_chest: 'open' });
  const loot = page.locator('.toast[data-kind="LOOT"]');
  await expect(loot.locator('.toast-subject')).toHaveText("Woodcutter's Hatchet");
  await expect(loot).toHaveAttribute('data-tier', 'E');
  await expect(loot.locator('.toast-hint')).toHaveText(
    controls === 'keyboard' ? 'Better than your blade. Press I to compare.' : 'Better than your blade. Tap Bag to compare.');

  await interact(page, controls);                                    // an open chest is not offered again
  await page.waitForTimeout(500);
  await expect(page.locator('.toast[data-kind="ERROR"]')).toHaveCount(0);

  await openBag(page, controls);
  const before = await position(page);
  if (controls === 'keyboard') {
    await page.keyboard.press('s');                                  // moves the highlight, not the player
    await page.keyboard.press('w');
    expect(await position(page)).toEqual(before);
    await page.keyboard.press('e');                                  // the hatchet: best tier first
  } else {
    await press(page.getByRole('button', { name: 'Compare' }));
  }
  const compare = page.getByRole('region', { name: "Compare Woodcutter's Hatchet" });
  await expect(compare).toBeVisible();
  const attack = compare.locator('tr', { has: page.getByRole('rowheader', { name: 'AR', exact: true }) });
  await expect(attack).toHaveClass(/compare-better/);
  await expect(attack.locator('td').first()).toHaveText('20');
  await expect(attack.locator('.compare-after')).toHaveText('31');
  if (controls === 'touch') await expectTouchTargets(page, `compare (${testInfo.project.name})`);

  if (controls === 'keyboard') await page.keyboard.press('e');      // Equip is highlighted first
  else await press(compare.getByRole('button', { name: 'Equip' }));
  await expect(compare).toBeHidden();
  await expect(page.locator('.hud-weapon')).toContainText("Woodcutter's Hatchet");
  await expect(page.locator('.hud-weapon')).toContainText('AR 31');
  await expect(page.locator('.weapon-card', { hasText: 'Rusty Shortsword' }).getByRole('button', { name: 'Compare' })).toBeVisible();
});

test("the secret clearing: through the gap behind the south trees, the Hunter's Longbow", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'one layout is enough for a walk');
  await toTheRuinsAtDawn(page, 'keyboard', `e2e-clearing-${Date.now() % 1e9}`);
  await walkTo(page, 'keyboard', 'to_lane');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, 'keyboard', 'clearing_chest');
  await interact(page, 'keyboard');
  await expect.poll(() => chests(page)).toEqual({ clearing_chest: 'open' });
  await expect(page.locator('.toast[data-kind="LOOT"] .toast-subject')).toHaveText("Hunter's Longbow");
  await expect(page.locator('.toast[data-kind="LOOT"]')).toHaveAttribute('data-tier', 'D');
});


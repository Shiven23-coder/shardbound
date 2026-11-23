import { expect, test, type Page } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import {
  beatTheMeadow, choose, heldShards, interact, JUNCTION_REST, leaveLantern, press, restAtLantern, status, tap,
  toTheLanternWithTheShard, walkToTile, type Controls,
} from '../support/world';

// The Junction Lantern through the real UI in each layout (V5 acceptance, P-61):
// cold until the Heartshard, kindled, rested at; a level bought with the
// keyboard (desktop) or taps (phones); a skill moved in the loadout; every
// target a thumb's size. On a slow network + and − never wait for the server.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

/** Faces the Lantern from its rest spawn and uses it. */
async function useLantern(page: Page, controls: Controls): Promise<void> {
  await walkToTile(page, controls, JUNCTION_REST);
  await expect.poll(async () => {
    await tap(page, controls, 'RIGHT', 1);
    return (await status(page))?.facing;
  }, { timeout: 10_000 }).toBe('RIGHT');
  await interact(page, controls);
}

test('the Lantern: cold, kindled, a level bought, a skill moved', async ({ page }, testInfo) => {
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  const box = page.locator('.lantern-box');
  await toTheLanternWithTheShard(page, controls, `e2e-lantern-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`, true);
  expect(await heldShards(page)).toBe(70);

  await useLantern(page, controls);                                  // cold: the Heartshard kindles it
  await expect(page.getByRole('heading', { name: 'Junction Lantern' })).toBeVisible();
  await choose(page, 'Warm your hands');
  await expect(page.locator('.dialogue-box')).toBeHidden();
  await interact(page, controls);                                    // lit: a rest
  await expect(box).toBeVisible();
  await expect(box.getByText('Spend Shards here')).toBeVisible();     // the first menu's tip
  if (controls === 'touch') await expectTouchTargets(page, 'the Lantern menu');
  await leaveLantern(page);

  await beatTheMeadow(page, controls);                               // the rest brought them back: 140 Shards
  await restAtLantern(page, controls);
  await expect(box.getByText('Spend Shards here')).toBeHidden();      // shown once
  if (controls === 'keyboard') {
    await page.keyboard.press('e');                                  // Level up (highlighted)
    await page.keyboard.press('d');                                  // + Vigor
    for (let i = 0; i < 6; i++) await page.keyboard.press('s');       // down to Confirm
    await expect(box.locator('.lantern-cost')).toContainText('100');
    await page.keyboard.press('e');
  } else {
    await press(box.getByRole('button', { name: /^Level up/ }));
    await press(box.getByRole('button', { name: 'Raise Vigor' }));
    await expectTouchTargets(page, 'the level-up screen');
    await expect(box.locator('.lantern-cost')).toContainText('100');
    await press(box.getByRole('button', { name: /^Confirm/ }));
  }
  await expect(box.locator('.lantern-summary')).toContainText('Level 2');
  await expect(box.locator('.lantern-shards')).toContainText('40');

  await press(box.getByRole('button', { name: 'Back' }));
  await press(box.getByRole('button', { name: /^Skills/ }));
  await press(box.getByRole('button', { name: /^Empty/ }).first());   // slot 3
  await press(box.getByRole('button', { name: /^Rending Cut/ }));
  await expect(box.locator('.lantern-slots .lantern-option').nth(2)).toContainText('Rending Cut');
  await expect(box.locator('.lantern-slots .lantern-option').nth(0)).toContainText('Empty');   // moved, not copied
  await press(box.getByRole('button', { name: 'Back' }));
  await leaveLantern(page);
  expect(await heldShards(page)).toBe(40);
});

test('on a slow network + and − answer at once; the preview follows', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'one browser is enough for the network check');
  await toTheLanternWithTheShard(page, 'keyboard', `e2e-slownet-${Date.now() % 1e9}`, true);
  await restAtLantern(page, 'keyboard');
  const box = page.locator('.lantern-box');
  await page.keyboard.press('e');                                    // Level up
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 1500, downloadThroughput: -1, uploadThroughput: -1 });

  const vigor = box.locator('.lantern-attribute').first();
  // Each press shows within 500 ms, a third of one round trip: none waited on the network.
  // (Per press, so a slow test runner sending the keys cannot make it pass or fail.)
  for (let i = 1; i <= 3; i++) {
    await page.keyboard.press('d');
    await expect(vigor).toContainText(`+${i}`, { timeout: 500 });
  }
  const cost = box.locator('.lantern-cost');
  await expect(box.locator('.lantern-preview-row').nth(1)).toContainText('…');   // Shards: on their way
  await expect(cost).toContainText('903', { timeout: 10_000 });     // 100 + 283 + 520 for three levels
  await page.keyboard.press('a');
  await expect(vigor).toContainText('+2', { timeout: 500 });
  await expect(cost).toContainText('383', { timeout: 10_000 });     // never the older answer
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
});

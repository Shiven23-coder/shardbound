import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';
import {
  heldShards, JUNCTION_REST, leaveLantern, loseBattle, lostShardsTile, position, restAtLantern, tileOf,
  toTheLanternWithTheShard, walkNear, walkTo, winBattle, type Controls,
} from '../support/world';

// Dying and getting the Shards back through the real UI (V2_SPEC §4.4, P-62):
// after resting at the Junction Lantern the player beats the slime, then lets
// the Storm Rat win; they wake beside the Lantern with no Shards, the marker
// glows where the battle started, and walking back picks the Shards up.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('a death drops the Shards; walking back recovers them', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'phone-landscape', 'desktop and portrait cover the flow');
  const controls: Controls = testInfo.project.use.hasTouch ? 'touch' : 'keyboard';
  await toTheLanternWithTheShard(page, controls, `e2e-death-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await restAtLantern(page, controls);
  await leaveLantern(page);

  await walkTo(page, controls, 'meadow_creep_b');
  await winBattle(page);
  expect(await heldShards(page)).toBe(35);
  await walkTo(page, controls, 'meadow_creep_a');
  await expect(page.locator('.battle-box')).toBeVisible({ timeout: 20_000 });
  const fell = tileOf((await position(page))!);
  expect(await loseBattle(page)).toBe('You wake beside the Junction Lantern.');   // read before Continue
  await expect.poll(async () => tileOf((await position(page))!)).toEqual(JUNCTION_REST);
  expect(await heldShards(page)).toBe(0);
  await expect.poll(() => lostShardsTile(page)).toEqual(fell);

  await walkNear(page, controls, fell);                               // the scene sends the recovery itself
  await expect.poll(() => heldShards(page), { timeout: 15_000 }).toBe(35);
  await expect.poll(() => lostShardsTile(page)).toBeNull();
});

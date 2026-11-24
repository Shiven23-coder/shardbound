import { expect, test, type Page } from '@playwright/test';
import * as backend from '../support/backend';
import { expectTouchTargets } from '../support/touch';
import {
  choose, fightTheHerald, holdSkip, inArea, interact, loseBattle, newGame, press, readyForTheHerald, toTheHerald,
  toTheRuinsLanternAfterTheAshFalls, turnPage, walkTo, type Controls,
} from '../support/world';

// The Ashen Herald through the real UI in every layout (P-67, P-68).
//  - The intro, after the elder's path played through the UI (the Heartshard,
//    Maren, "The Ash Falls", the Ruins Lantern): a tap advances a line;
//    letting go of a hold early does not skip; a full hold of E (keyboard) or
//    the Skip button (touch) does, into the fight, its name banner and boss bar.
//  - A death wakes the player at the Ruins Lantern, and the way back onto the
//    Rise goes straight to the fight: the banner again, no intro.
//  - A win, from the e2e profile's rehearsal (level 4 with the hatchet, as the
//    balance bot meets the Herald): FOE VANQUISHED, the epilogue, free roam in
//    the changed Eldmere, and the west road's stop where the flood was.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

const controlsOf = (hasTouch: boolean | undefined): Controls => (hasTouch ? 'touch' : 'keyboard');

/** A new game played past the storm intro, then rehearsed to the Herald's door (win test only) and onto the trigger. */
async function rehearsedAtTheHerald(page: Page, controls: Controls, username: string): Promise<void> {
  await newGame(page, username);
  for (let turns = 0; turns < 60 && !(await page.locator('.dialogue-body > .choice-list').isVisible()); turns++) await turnPage(page);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await readyForTheHerald(page);
  await toTheHerald(page, controls);
}

test('the intro advances by taps, skips only on a full hold, and never plays again after a death', async ({ page }, testInfo) => {
  test.setTimeout(480_000);
  const controls = controlsOf(testInfo.project.use.hasTouch);
  await toTheRuinsLanternAfterTheAshFalls(page, controls, `e2e-herald-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await toTheHerald(page, controls);

  const line = page.locator('.cutscene-line');
  await expect(line).toBeVisible({ timeout: 30_000 });
  await expect(line).toContainText('Little lamp-bearer. You carry what was ours.', { timeout: 10_000 });
  await expect(page.locator('.hud-cluster')).toBeHidden();             // a cutscene hides the HUD
  if (testInfo.project.use.hasTouch) await expectTouchTargets(page, 'the cutscene');

  await press(line);                                                    // completes the typing (or it already has)
  await press(line);                                                    // the next line
  await expect(line).toContainText('The Circle wakes the world', { timeout: 15_000 });

  await holdSkip(page, controls, 400);                                  // let go early: only the hint
  await expect(page.getByRole('status').filter({ hasText: /^Hold (.+ )?to skip$/ })).toBeVisible();
  await expect(page.locator('.battle-box')).toBeHidden();
  await holdSkip(page, controls, 1400);                                 // a full hold: skipped
  await expect(page.locator('.battle-box')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.boss-banner-name')).toHaveText('Ashen Herald');
  await expect(page.locator('.boss-bar')).toContainText('Ashen Herald');

  expect(await loseBattle(page)).toBe('You wake beside the Ruins Lantern.');
  await inArea(page, 'The Old Ruins');
  await toTheHerald(page, controls);
  await expect(page.locator('.battle-box')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.boss-banner-name')).toHaveText('Ashen Herald');
  await expect(line).toBeHidden();                                       // no intro the second time
});

test('a win: FOE VANQUISHED, the epilogue, then free roam and the west road stops at the far bank', async ({ page }, testInfo) => {
  test.setTimeout(600_000);
  test.skip(testInfo.project.name === 'phone-landscape', 'desktop and portrait cover the win; the first test covers landscape');
  const controls = controlsOf(testInfo.project.use.hasTouch);
  await rehearsedAtTheHerald(page, controls, `e2e-heraldw-${testInfo.project.name.slice(0, 7)}-${Date.now() % 1e9}`);
  await expect(page.locator('.cutscene-line')).toBeVisible({ timeout: 30_000 });
  await holdSkip(page, controls, 1400);

  let vanquished = false;
  const won = await fightTheHerald(page, controls, 3, async () => {
    vanquished ||= await page.locator('.boss-banner-vanquished').isVisible();
  });
  expect(won).toBe(true);
  expect(vanquished, 'FOE VANQUISHED showed').toBe(true);

  const line = page.locator('.cutscene-line');
  await expect(line).toContainText('The ash stops falling.', { timeout: 15_000 });
  for (let turns = 0; turns < 20 && (await line.isVisible()); turns++) {
    await press(line);
    await page.waitForTimeout(300);
  }
  await expect(page.locator('.hud-cluster')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('world-canvas').locator('canvas')).not.toHaveAttribute('data-bosses', /ashen_herald/);

  await walkTo(page, controls, 'to_ruins');
  await inArea(page, 'The Old Ruins');
  await walkTo(page, controls, 'to_lane');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, controls, 'road_stop');
  await interact(page, controls);
  await expect(page.getByRole('heading', { name: 'The West Road' })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.dialogue-box')).toContainText('The far bank is still sodden; tomorrow, perhaps.');
});

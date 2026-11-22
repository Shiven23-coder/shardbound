import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';
import { choose, inArea, newGame, walkTo } from '../support/world';

// P-30: a reload must never lose the run. Each cycle stops the backend,
// reloads the page while the API is down, then starts the backend again; the
// player must land back in the same place in the world without doing anything.
// 5 cycles on PRs, 20 nightly (P-36): RESUME_CYCLES overrides.
const CYCLES = Number(process.env.RESUME_CYCLES ?? 5);

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test(`the run survives ${CYCLES} backend restarts with a reload during each`, async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: 'New game' }).click();
  await page.getByLabel('Username').fill(`e2e-resume-${Date.now()}`);
  await page.getByRole('button', { name: 'Start' }).click();
  await expect(page.getByRole('heading', { name: 'Storm Night' })).toBeVisible();
  await choose(page, 'Help Corvin lash down his wagon');
  const lane = page.locator('.world-screen[data-area-name="Cottage Lane"]');
  const world = page.getByTestId('world-canvas').locator('canvas');
  await expect(lane).toBeVisible();
  await expect(world).toBeVisible();

  for (let cycle = 1; cycle <= CYCLES; cycle++) {
    await test.step(`cycle ${cycle}: restart the backend while reloading`, async () => {
      await backend.stop();
      await page.reload();
      await backend.start();
      await expect(lane, `cycle ${cycle}: the run should resume on its own`).toBeVisible({ timeout: 30_000 });
      await expect(world, `cycle ${cycle}: the world should be drawn again`).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Storm Night' })).toBeHidden();
    });
  }
});

// An exit stepped on while the network is down cannot reach the server. Once the
// game reconnects, the client sends the same request again with the same
// idempotency key (P-56), so the exit simply goes through, once.
test('an exit tried while offline goes through once the game reconnects', async ({ page, context }) => {
  await newGame(page, `e2e-offline-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon');
  await inArea(page, 'Cottage Lane');

  await context.setOffline(true);
  await walkTo(page, 'keyboard', 'to_ruins').catch(() => undefined);   // the exit is sent and fails: no next area
  const reconnecting = page.getByText('Reconnecting…');
  await expect(reconnecting).toBeVisible({ timeout: 15_000 });
  await context.setOffline(false);
  await expect(reconnecting).toBeHidden({ timeout: 30_000 });

  await inArea(page, 'The Old Ruins');
});

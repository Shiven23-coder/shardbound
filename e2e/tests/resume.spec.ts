import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';

// P-30: a reload must never lose the run. Each cycle stops the backend,
// reloads the page while the API is down, then starts the backend again; the
// player must land back in the same scene without doing anything.
// 5 cycles on PRs, 20 nightly (P-36): RESUME_CYCLES overrides.
const CYCLES = Number(process.env.RESUME_CYCLES ?? 5);

test.afterAll(async () => {
  await backend.stop();
});

test(`the run survives ${CYCLES} backend restarts with a reload during each`, async ({ page }) => {
  await backend.start();
  await page.goto('/');

  await page.getByRole('button', { name: 'New game' }).click();
  await page.getByLabel('Username').fill(`e2e-resume-${Date.now()}`);
  await page.getByRole('button', { name: 'Start' }).click();
  await expect(page.getByRole('heading', { name: 'Storm Night' })).toBeVisible();
  await page.getByText('Help Corvin lash down his wagon').click();
  const ruins = page.getByRole('heading', { name: 'The Ruins' });
  await expect(ruins).toBeVisible();

  for (let cycle = 1; cycle <= CYCLES; cycle++) {
    await test.step(`cycle ${cycle}: restart the backend while reloading`, async () => {
      await backend.stop();
      await page.reload();
      await backend.start();
      await expect(ruins, `cycle ${cycle}: the run should resume on its own`).toBeVisible({ timeout: 30_000 });
    });
  }
});

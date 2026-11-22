import { expect, test } from '@playwright/test';
import * as backend from '../support/backend';
import { choose, inArea, newGame, position, press, turnPage, walkTo } from '../support/world';

// The touch controls on a phone (P-40, P-51): a finger on the D-pad when a
// dialogue takes its place must not leave the player walking once the
// dialogue ends, and holding a control must never start a text selection.

test.beforeAll(async () => {
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('a finger on the D-pad when a dialogue opens does not keep the player walking afterwards', async ({ page }) => {
  await newGame(page, `e2e-dpad-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await walkTo(page, 'touch', 'flooded_road');
  const before = await position(page);
  // One finger holds "Walk down" while another taps Ⓐ: the flooded road's message opens and
  // takes the controls' place (portrait) or covers them (landscape).
  await page.evaluate(() => {
    const button = (label: string) => document.querySelector(`button[aria-label="${label}"]`)!;
    button('Walk down').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerType: 'touch', pointerId: 11, isPrimary: true }));
    button('Talk or use').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerType: 'touch', pointerId: 12, isPrimary: false }));
  });
  await expect(page.getByRole('heading', { name: 'The Flooded Road' })).toBeVisible();
  // Both fingers lift wherever they now are.
  await page.evaluate(() => {
    for (const pointerId of [11, 12]) {
      document.body.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerType: 'touch', pointerId }));
    }
  });
  const close = page.locator('.dialogue-close');
  for (let i = 0; i < 20 && !(await close.isVisible()); i++) await turnPage(page);
  await press(close);
  await expect(page.locator('.dialogue-box')).toBeHidden();
  await page.waitForTimeout(2000);
  expect(await position(page), 'nobody is touching the D-pad').toEqual(before);
});

test('holding a control or the deck never selects text', async ({ page }) => {
  await newGame(page, `e2e-select-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await expect(page.getByRole('button', { name: 'Walk right' })).toBeVisible();
  const styles = await page.evaluate(() => [...document.querySelectorAll('.world-screen, .world-screen *')]
    .filter((e) => e instanceof HTMLElement && e.offsetParent !== null)
    .map((e) => ({ name: e.className || e.tagName, select: getComputedStyle(e).userSelect, callout: getComputedStyle(e).getPropertyValue('-webkit-touch-callout') }))
    .filter((s) => s.select !== 'none' || (s.callout !== '' && s.callout !== 'none')));
  expect(styles, 'elements whose text a long press would select').toEqual([]);
});

test("a long press on the D-pad or Ⓐ starts none of the browser's own gestures", async ({ page }) => {
  await newGame(page, `e2e-press-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');
  await inArea(page, 'Cottage Lane');
  await expect(page.getByRole('button', { name: 'Walk right' })).toBeVisible();
  const prevented = await page.evaluate(() => {
    const result: Record<string, boolean> = {};
    for (const label of ['Walk right', 'Walk up', 'Talk or use']) {
      const button = document.querySelector(`button[aria-label="${label}"]`)!;
      const touch = new Touch({ identifier: 21, target: button, clientX: 1, clientY: 1 });
      const start = new TouchEvent('touchstart', { bubbles: true, cancelable: true, touches: [touch], targetTouches: [touch], changedTouches: [touch] });
      button.dispatchEvent(start);
      button.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true, changedTouches: [touch] }));
      result[`touchstart on ${label}`] = start.defaultPrevented;
    }
    const menu = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
    document.querySelector('.world-view')!.dispatchEvent(menu);
    result['context menu on the world'] = menu.defaultPrevented;
    return result;
  });
  expect(prevented).toEqual({
    'touchstart on Walk right': true, 'touchstart on Walk up': true, 'touchstart on Talk or use': true, 'context menu on the world': true,
  });
});

test('the first tap in the game goes full screen, and the button brings it back', async ({ page }) => {
  await newGame(page, `e2e-full-${Date.now() % 1e9}`);
  await choose(page, 'Help Corvin lash down his wagon before the wind takes it');   // taps inside the game
  await inArea(page, 'Cottage Lane');
  const fullscreen = () => page.evaluate(() => document.fullscreenElement !== null);
  await expect.poll(fullscreen, { message: 'full screen after the first tap' }).toBe(true);
  await page.evaluate(() => document.exitFullscreen());                                 // the player leaves full screen
  await expect.poll(fullscreen).toBe(false);
  await page.waitForTimeout(500);
  await page.getByRole('button', { name: 'Walk right' }).tap();
  expect(await fullscreen(), 'not forced back after the player left it').toBe(false);
  const button = page.getByRole('button', { name: 'Full screen' });
  await expect(button).toBeVisible();
  await button.tap();
  await expect.poll(fullscreen).toBe(true);
  await expect(button).toBeHidden();
});

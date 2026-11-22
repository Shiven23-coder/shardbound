import { expect, type Page } from '@playwright/test';

const MIN = 48;
const INTERACTIVE = 'button, a[href], [role="button"], [role="tab"], input, select, textarea';

/** Every visible interactive element on the page is at least 48 × 48 px (§4.16). */
export async function expectTouchTargets(page: Page, screen: string): Promise<void> {
  const small = await page.locator(INTERACTIVE).evaluateAll((elements, min) => elements
    .filter((el) => {
      const style = getComputedStyle(el);
      const box = el.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && box.width > 0 && box.height > 0;
    })
    .map((el) => {
      const box = el.getBoundingClientRect();
      const name = el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 30) ?? el.tagName;
      return { name, width: Math.round(box.width), height: Math.round(box.height) };
    })
    .filter((t) => t.width < min || t.height < min), MIN);
  expect(small, `touch targets under ${MIN} px on ${screen}`).toEqual([]);
}

import { describe, expect, it } from 'vitest';
import { zoomFor } from './zoom';

const SQUARE = { width: 40 * 16, height: 28 * 16 };   // the widest Eldmere map
const LANE = { width: 36 * 16, height: 24 * 16 };

describe('camera zoom (P-29, P-40, P-52)', () => {
  it('keeps about 11 tiles across the short side where that still gives a zoom of 2 or more', () => {
    expect(zoomFor({ width: 1280, height: 800 }, SQUARE)).toBe(4);   // desktop
    expect(zoomFor({ width: 390, height: 600 }, LANE)).toBe(2);      // portrait world above the deck
  });

  it('a short landscape phone view (address bar showing) zooms to 2, not 1', () => {
    expect(zoomFor({ width: 873, height: 291 }, LANE)).toBe(2);
  });

  it('the map always fills the view: never a black band beside a map narrower than the screen', () => {
    for (const view of [{ width: 873, height: 291 }, { width: 1527, height: 330 }, { width: 2560, height: 400 }]) {
      const zoom = zoomFor(view, SQUARE);
      expect(SQUARE.width * zoom, `${view.width}×${view.height}`).toBeGreaterThanOrEqual(view.width);
      expect(SQUARE.height * zoom).toBeGreaterThanOrEqual(view.height);
    }
  });

  it('is always a whole number of at least 1', () => {
    expect(zoomFor({ width: 100, height: 80 }, { width: 2000, height: 2000 })).toBe(1);
    expect(Number.isInteger(zoomFor({ width: 999, height: 333 }, LANE))).toBe(true);
  });
});

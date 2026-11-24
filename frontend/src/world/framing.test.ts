import { describe, expect, it } from 'vitest';
import { frameSubjects, FRAME_MARGIN_PX, union } from './framing';
import type { Box, Framing } from './framing';
import type { Size } from './zoom';

// The Herald's intro on the Ash Rise (20 × 14 tiles), in the three e2e layouts (P-68).
const RISE: Size = { width: 320, height: 224 };
const herald: Box = { left: 146, top: 36, right: 191, bottom: 81 };       // standing at (10, 4)
const player: Box = { left: 96, top: 92, right: 112, bottom: 108 };       // on the trigger row at (6, 6)
const both = union([herald, player]);

/** Each subject's box on screen, in screen pixels. */
function onScreen(box: Box, framing: Framing, view: Size): Box {
  const left = framing.x - view.width / framing.zoom / 2;
  const top = framing.y - view.height / framing.zoom / 2;
  return {
    left: (box.left - left) * framing.zoom,
    top: (box.top - top) * framing.zoom,
    right: (box.right - left) * framing.zoom,
    bottom: (box.bottom - top) * framing.zoom,
  };
}

function expectInPlayArea(box: Box, view: Size, cover: number): void {
  expect(box.left).toBeGreaterThanOrEqual(0);
  expect(box.top).toBeGreaterThanOrEqual(0);
  expect(box.right).toBeLessThanOrEqual(view.width);
  expect(box.bottom).toBeLessThanOrEqual(view.height * (1 - cover));
}

describe('frameSubjects', () => {
  it.each([
    ['desktop', { width: 1280, height: 800 }, 1 / 3, 4],
    ['phone landscape', { width: 844, height: 390 }, 1 / 3, 3],
    ['phone portrait (the world above the deck)', { width: 390, height: 500 }, 0, 3],
  ])('keeps both actors above the dialogue box on %s', (_, view, cover, zoom) => {
    const framing = frameSubjects(both, view, cover, RISE, zoom);
    expectInPlayArea(onScreen(herald, framing, view), view, cover);
    expectInPlayArea(onScreen(player, framing, view), view, cover);
  });

  it('centres the subjects in the play area when the map leaves room', () => {
    const view = { width: 1280, height: 800 };
    const big: Size = { width: 2000, height: 2000 };
    const subject: Box = { left: 990, top: 990, right: 1010, bottom: 1010 };
    const framing = frameSubjects(subject, view, 1 / 3, big, 4);
    const box = onScreen(subject, framing, view);
    expect((box.top + box.bottom) / 2).toBeCloseTo((800 * (2 / 3)) / 2);
    expect((box.left + box.right) / 2).toBeCloseTo(640);
  });

  it('steps the zoom down to fit, but never below the zoom that fills the view with the map', () => {
    const view = { width: 400, height: 400 };
    const tall: Box = { left: 100, top: 100, right: 120, bottom: 300 };
    const map: Size = { width: 1000, height: 1000 };
    expect(frameSubjects(tall, view, 0, map, 4).zoom).toBe(1);
    expect(frameSubjects(tall, view, 0, { width: 200, height: 200 }, 4).zoom).toBe(2);
    expect(FRAME_MARGIN_PX).toBeGreaterThan(0);
  });

  it('keeps the camera inside the map', () => {
    const view = { width: 844, height: 390 };
    const framing = frameSubjects({ left: 0, top: 0, right: 16, bottom: 16 }, view, 1 / 3, RISE, 3);
    expect(framing.x).toBeCloseTo(844 / 3 / 2);
    expect(framing.y).toBeCloseTo(390 / 3 / 2);
  });
});

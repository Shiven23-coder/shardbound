// Cutscene framing (P-68): where the camera looks, and at what whole zoom, so
// every subject sits inside the visible play area: the view less the dialogue
// box over its bottom share and a margin at every edge, with the camera kept
// inside the map. The zoom only steps down (whole numbers, P-29) while the map
// still fills the view (P-52). No Phaser here.
import { fillZoom } from './zoom';
import type { Size } from './zoom';

/** A rectangle in world pixels. */
export interface Box {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

/** The camera's centre in world pixels and its zoom. */
export interface Framing {
  x: number;
  y: number;
  zoom: number;
}

/** Screen pixels kept clear at every edge of the play area. */
export const FRAME_MARGIN_PX = 12;

/** The smallest box holding all of these. */
export function union(boxes: Box[]): Box {
  return {
    left: Math.min(...boxes.map((b) => b.left)),
    top: Math.min(...boxes.map((b) => b.top)),
    right: Math.max(...boxes.map((b) => b.right)),
    bottom: Math.max(...boxes.map((b) => b.bottom)),
  };
}

/**
 * Frames `subjects` in a `view` (screen pixels) whose bottom `cover` share is
 * under the dialogue box: the subjects' centre goes to the middle of the play
 * area above it, at the largest whole zoom up to `zoom` that fits them, and the
 * camera is clamped to the map.
 */
export function frameSubjects(subjects: Box, view: Size, cover: number, map: Size, zoom: number): Framing {
  const playWidth = view.width - 2 * FRAME_MARGIN_PX;
  const playHeight = view.height * (1 - cover) - 2 * FRAME_MARGIN_PX;
  const width = subjects.right - subjects.left;
  const height = subjects.bottom - subjects.top;
  const lowest = Math.min(zoom, fillZoom(view, map));
  let z = zoom;
  while (z > lowest && (width * z > playWidth || height * z > playHeight)) z--;

  const halfWidth = view.width / z / 2;
  const halfHeight = view.height / z / 2;
  const centreX = (subjects.left + subjects.right) / 2;
  // The play area's middle sits half the cover above the view's middle.
  const centreY = (subjects.top + subjects.bottom) / 2 + (view.height * cover) / 2 / z;
  return {
    x: clamp(centreX, halfWidth, map.width - halfWidth),
    y: clamp(centreY, halfHeight, map.height - halfHeight),
    zoom: z,
  };
}

/** `value` kept between `low` and `high`; the middle of the two when the range is empty (a map smaller than the view). */
function clamp(value: number, low: number, high: number): number {
  return low > high ? (low + high) / 2 : Math.min(high, Math.max(low, value));
}

// The camera zoom: a whole number, so 16 px tiles stay pixel-perfect (P-29).
// No Phaser here.

const TILE = 16;
/** Tiles across the short side of the view the zoom aims for (P-40). */
const SHORT_SIDE_TILES = 11;
/** On a short view (a phone on its side with the address bar showing), fewer tiles rather than a zoom of 1 (P-52). */
const MIN_SHORT_SIDE_TILES = 8;

export interface Size {
  width: number;
  height: number;
}

/**
 * The largest whole zoom that keeps SHORT_SIDE_TILES across the short side of
 * the view; if that is only 1, the largest that keeps MIN_SHORT_SIDE_TILES;
 * and at least enough for the map to fill the view, so no empty band ever
 * shows beside a map narrower or shorter than the screen (P-52).
 */
export function zoomFor(view: Size, map: Size): number {
  const short = Math.min(view.width, view.height);
  let zoom = Math.floor(short / (SHORT_SIDE_TILES * TILE));
  if (zoom < 2) zoom = Math.floor(short / (MIN_SHORT_SIDE_TILES * TILE));
  const fill = Math.max(Math.ceil(view.width / map.width), Math.ceil(view.height / map.height));
  return Math.max(1, zoom, fill);
}

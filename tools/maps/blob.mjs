// Reads a 47-tile "blob" autotile block straight from the tileset's pixels.
// Each tile is sampled in 8 directions; a sample counts as "fill" (dirt,
// water, …) unless it matches the base colour (grass). The result maps each
// neighbour mask to a tile id, so maps get correct edges, corners and inner
// corners without anyone transcribing ids by hand.
import { readFileSync } from 'node:fs';
import { readPng } from './png.mjs';

// Directions in mask-bit order, with the border region of a 16×16 tile each
// one samples: [x0, y0, x1, y1) in pixels.
const DIRECTIONS = [
  { name: 'N', bit: 1, area: [4, 0, 12, 4], dx: 0, dy: -1 },
  { name: 'NE', bit: 2, area: [12, 0, 16, 4], dx: 1, dy: -1 },
  { name: 'E', bit: 4, area: [12, 4, 16, 12], dx: 1, dy: 0 },
  { name: 'SE', bit: 8, area: [12, 12, 16, 16], dx: 1, dy: 1 },
  { name: 'S', bit: 16, area: [4, 12, 12, 16], dx: 0, dy: 1 },
  { name: 'SW', bit: 32, area: [0, 12, 4, 16], dx: -1, dy: 1 },
  { name: 'W', bit: 64, area: [0, 4, 4, 12], dx: -1, dy: 0 },
  { name: 'NW', bit: 128, area: [0, 0, 4, 4], dx: -1, dy: -1 },
];
const FILL_SHARE = 0.3; // a region counts as filled when ≥ 30 % of its pixels are fill
const [N, NE, E, SE, S, SW, W, NW] = DIRECTIONS.map((d) => d.bit);

/** A corner only counts when both edges next to it are filled too (blob rule). */
export function canonical(mask) {
  let m = mask;
  if (!(m & N && m & E)) m &= ~NE;
  if (!(m & S && m & E)) m &= ~SE;
  if (!(m & S && m & W)) m &= ~SW;
  if (!(m & N && m & W)) m &= ~NW;
  return m;
}

function pixel(png, x, y) {
  const i = (y * png.width + x) * 4;
  return [png.data[i], png.data[i + 1], png.data[i + 2]];
}

const distance = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);

/**
 * @param file   tileset PNG (16×16 tiles)
 * @param block  { col, row, width, height } of the autotile block, in tiles
 * @param baseId a tile id that is pure base (grass), used as the colour reference
 * @returns Map from canonical mask to tile id (first tile found wins)
 */
export function readBlob(file, block, baseId) {
  const png = readPng(readFileSync(file));
  const columns = png.width / 16;
  const base = pixel(png, (baseId % columns) * 16 + 8, Math.floor(baseId / columns) * 16 + 8);
  const table = new Map();
  for (let r = block.row; r < block.row + block.height; r++) {
    for (let c = block.col; c < block.col + block.width; c++) {
      const centre = pixel(png, c * 16 + 8, r * 16 + 8);
      const alpha = png.data[((r * 16 + 8) * png.width + c * 16 + 8) * 4 + 3];
      if (alpha === 0) continue; // empty slot
      let mask = 0;
      for (const d of DIRECTIONS) {
        const [x0, y0, x1, y1] = d.area;
        let fill = 0;
        for (let y = y0; y < y1; y++) {
          for (let x = x0; x < x1; x++) if (distance(pixel(png, c * 16 + x, r * 16 + y), base) >= 40) fill++;
        }
        if (fill / ((x1 - x0) * (y1 - y0)) >= FILL_SHARE) mask |= d.bit;
      }
      // Convex corners have grass in the middle: keep any tile with fill somewhere.
      if (mask === 0 && distance(centre, base) < 40) continue;
      const key = canonical(mask);
      if (!table.has(key)) table.set(key, r * columns + c);
    }
  }
  return table;
}

/** The mask of a cell from a predicate telling which neighbours are filled. */
export function neighbourMask(isFilled, x, y) {
  let mask = 0;
  for (const d of DIRECTIONS) if (isFilled(x + d.dx, y + d.dy)) mask |= d.bit;
  return canonical(mask);
}

/** Best tile for a mask: exact match, else drop corner bits, else the centre tile. */
export function pick(table, mask) {
  if (table.has(mask)) return table.get(mask);
  const edgesOnly = mask & (N | E | S | W);
  if (table.has(edgesOnly)) return table.get(edgesOnly);
  return table.get(canonical(N | NE | E | SE | S | SW | W | NW));
}

// The battle backdrop for Eldmere (P-57), composited from the graded pack
// tiles: a treeline strip (a darker back row of pines, a front row of round
// trees) above a strip of meadow grass. Both strips tile horizontally (trees
// that cross the right edge wrap to the left) and the grass also tiles
// vertically, so the battle scene can repeat them across any screen at the
// sprites' pixel scale.
// Output: content/art/shardbound/battle-eldmere.png, 256 px wide:
//   rows 0–47   treeline (transparent above the trees)
//   rows 48–79  grass
//   node tools/art/backdrop.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { blankImage, readPng, writePng } from '../maps/png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;
const WIDTH = 256;
const TREELINE = 48;
const GRASS = 32;

const tileset = (name) => readPng(readFileSync(path.join(ROOT, 'content/art/ninja-adventure/eldmere/tilesets', name)));
const nature = tileset('TilesetNature.png');
const floor = tileset('TilesetFloor.png');

/** Regions of the tilesets, in tiles: [x, y, width, height]. */
const PINES = [0, 2, 4, 3];
const GREEN_CLUSTER = [4, 2, 4, 3];
const OLIVE_CLUSTER = [16, 2, 4, 3];
const ROUND_TREE = [18, 0, 2, 2];
const GRASS_PLAIN = [0, 12];
const GRASS_TUFTS = [[1, 12], [2, 11], [3, 11]];

/**
 * Draws a tileset region onto the image with alpha blending, wrapping in x,
 * darkened by `shade` (1 = as is).
 */
function stamp(image, sheet, [rx, ry, rw, rh], dx, dy, shade = 1) {
  for (let y = 0; y < rh * TILE; y++) {
    for (let x = 0; x < rw * TILE; x++) {
      const from = ((ry * TILE + y) * sheet.width + rx * TILE + x) * 4;
      const alpha = sheet.data[from + 3] / 255;
      const ty = dy + y;
      if (alpha === 0 || ty < 0 || ty >= image.height) continue;
      const to = (ty * image.width + ((dx + x) % image.width + image.width) % image.width) * 4;
      for (let c = 0; c < 3; c++) {
        image.data[to + c] = Math.round(sheet.data[from + c] * shade * alpha + image.data[to + c] * (1 - alpha));
      }
      image.data[to + 3] = Math.max(image.data[to + 3], sheet.data[from + 3]);
    }
  }
}

const image = blankImage(WIDTH, TREELINE + GRASS);

// The back row: pines, darker, so the front trees stand out.
for (const x of [0, 72, 150, 206]) stamp(image, nature, PINES, x, 0, 0.62);
// The front row: round tree clusters and single trees, bottoms on the horizon.
for (const [region, x] of [[GREEN_CLUSTER, 28], [OLIVE_CLUSTER, 118], [GREEN_CLUSTER, 186]]) {
  stamp(image, nature, region, x, TREELINE - region[3] * TILE + 4, 0.85);
}
for (const x of [96, 236]) stamp(image, nature, ROUND_TREE, x, TREELINE - 2 * TILE + 2, 0.8);

// The grass: plain tiles with a few tufts in a fixed scatter.
const TUFT_AT = new Map([[3, 0], [9, 1], [13, 2], [18, 1], [22, 0], [28, 2]]);
for (let row = 0; row < GRASS / TILE; row++) {
  for (let column = 0; column < WIDTH / TILE; column++) {
    const index = row * (WIDTH / TILE) + column;
    const [tx, ty] = TUFT_AT.has(index) ? GRASS_TUFTS[TUFT_AT.get(index)] : GRASS_PLAIN;
    stamp(image, floor, [tx, ty, 1, 1], column * TILE, TREELINE + row * TILE);
  }
}

const out = path.join(ROOT, 'content/art/shardbound/battle-eldmere.png');
writeFileSync(out, writePng(image));
console.log(`wrote ${path.relative(ROOT, out)} (${WIDTH}×${TREELINE + GRASS})`);

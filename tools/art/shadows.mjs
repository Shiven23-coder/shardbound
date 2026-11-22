// Generates Shardbound's own soft drop-shadow tiles (map rule 5):
//   0, 1  left and right half of a tree shadow (an ellipse under a 2-tile trunk)
//   2     a small round shadow under a 1-tile prop
//   3     a soft band along the top of a tile, for the row under a building
// Output: content/art/shardbound/shadows.png (16×16 tiles in one row).
//   node tools/art/shadows.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { blankImage, writePng } from '../maps/png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;
const MAX_ALPHA = 0.42;
const image = blankImage(TILE * 4, TILE, [0, 0, 0, 0]);

function shade(x, y, strength) {
  const a = Math.max(0, Math.min(1, strength)) * MAX_ALPHA;
  const i = (y * image.width + x) * 4;
  image.data.set([12, 10, 20, Math.round(a * 255)], i);
}

// Soft ellipse: full strength inside, fading over the outer 40 %.
function ellipse(tileIndex, cx, cy, rx, ry) {
  for (let y = 0; y < TILE; y++) {
    for (let x = 0; x < TILE; x++) {
      const d = Math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry);
      if (d < 1) shade(tileIndex * TILE + x, y, d < 0.6 ? 1 : (1 - d) / 0.4);
    }
  }
}

ellipse(0, 16, 12.5, 13, 4.2); // left half: centre on the tile's right edge
ellipse(1, 0, 12.5, 13, 4.2); // right half: centre on the tile's left edge
ellipse(2, 8, 13, 7, 3);
for (let y = 0; y < 6; y++) for (let x = 0; x < TILE; x++) shade(3 * TILE + x, y, 1 - y / 6);

const out = path.join(ROOT, 'content', 'art', 'shardbound', 'shadows.png');
mkdirSync(path.dirname(out), { recursive: true });
writeFileSync(out, writePng(image));
console.log(`wrote ${path.relative(ROOT, out)}`);

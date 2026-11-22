// Shardbound's own small props, drawn pixel by pixel where the pack has no
// equivalent. Each prop is a grid of palette letters; '.' is transparent.
// Output: content/art/shardbound/props.png (16×16 tiles in one row):
//   0, 1  lantern post (top, base)
//   2     the Heartshard in the crater: a pale green crystal on rubble
//   3     millstone, seen from above
//   node tools/art/props.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { blankImage, writePng } from '../maps/png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;
const PALETTE = {
  o: [26, 18, 16, 255], // outline
  w: [92, 60, 40, 255], // wood
  W: [124, 84, 54, 255], // wood highlight
  m: [44, 44, 52, 255], // iron frame
  g: [255, 207, 106, 255], // lit glass
  G: [255, 240, 190, 255], // glass highlight
  h: [176, 112, 52, 255], // glass edge glow
  c: [128, 214, 150, 255], // crystal
  C: [214, 255, 222, 255], // crystal highlight
  d: [52, 128, 92, 255], // crystal shade
  r: [70, 62, 66, 255], // rubble
  R: [98, 88, 90, 255], // rubble highlight
  s: [104, 100, 104, 255], // stone
  S: [138, 132, 134, 255], // stone highlight
  k: [70, 66, 72, 255], // stone shade
};

// Lantern post, two tiles tall.
const LANTERN_POST = [
  '................',
  '......oooooooo..',
  '......owWWWWWo..',
  '......oooooowo..',
  '.....o.....owo..',
  '....omo....owo..',
  '...ommmo...owo..',
  '...ohGgo...owo..',
  '...ogggo...owo..',
  '...ohgho...owo..',
  '...ommmo...owo..',
  '....ooo....owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '...........owo..',
  '..........owwwo.',
  '.........owWWwwo',
  '.........owwwwwo',
  '..........ooooo.',
  '................',
  '................',
];

const HEARTSHARD = [
  '................',
  '.......o........',
  '......oCo.......',
  '......oCdo......',
  '.....oCcdo..o...',
  '.....oCcdo.oCo..',
  '..o..oCcddooCdo.',
  '.oCo.occddoccdo.',
  '.oCdooccdddccdo.',
  '.occdoccdddccdo.',
  '..ocdocccddcdo..',
  '.ooRrooooooooRo.',
  '.oRrrrRrrrRrrro.',
  '..oorrrrrrrrrro.',
  '....oooooooooo..',
  '................',
];

const image = blankImage(TILE * 4, TILE, [0, 0, 0, 0]);

/** Draws a letter grid, `TILE` columns wide, starting at tile `first`; taller grids continue in the next tiles. */
function draw(grid, first) {
  grid.forEach((line, y) => {
    if (line.length !== TILE) throw new Error(`row ${y} of tile ${first} is ${line.length} wide`);
    [...line].forEach((key, x) => {
      if (key === '.') return;
      const tile = first + Math.floor(y / TILE);
      image.data.set(PALETTE[key], ((y % TILE) * image.width + tile * TILE + x) * 4);
    });
  });
}

/** A round millstone with a centre hole, lit from the top left. */
function millstone(tile) {
  const cx = 8;
  const cy = 8.5;
  for (let y = 0; y < TILE; y++) {
    for (let x = 0; x < TILE; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);
      let key = null;
      if (d <= 6.8) key = 'o';
      if (d <= 6.0) key = dx + dy < -3 ? 'S' : dx + dy > 4 ? 'k' : 's';
      if (d > 3.6 && d < 4.3) key = 'k'; // the grinding groove
      if (d <= 1.9) key = 'o';
      if (key) image.data.set(PALETTE[key], (y * image.width + tile * TILE + x) * 4);
    }
  }
}

draw(LANTERN_POST, 0);
draw(HEARTSHARD, 2);
millstone(3);

const out = path.join(ROOT, 'content', 'art', 'shardbound', 'props.png');
mkdirSync(path.dirname(out), { recursive: true });
writeFileSync(out, writePng(image));
console.log(`wrote ${path.relative(ROOT, out)}`);

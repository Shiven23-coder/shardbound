// The chest sheet for a region (P-65): the pack's little treasure chest, closed
// as drawn, and an open frame redrawn in the pack's own colours with the lid
// thrown back and the empty inside showing, a little dimmer than the closed one
// so an opened chest reads as spent.
//
//   node tools/art/chest.mjs <region>
// Reads content/art/ninja-adventure/source/items/LittleTreasureChest.png and its
// graded copy for the region; writes content/art/shardbound/chest-<region>.png
// (two 16 × 16 frames: closed, open).
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { blankImage, readPng, writePng } from '../maps/png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;
const OPEN_BRIGHTNESS = 0.82;

/** Pack colours by letter, from the source sheet (outline, teal trim, wood, light trim, gold, orange band). */
const SOURCE_COLOURS = {
  a: [20, 27, 27], b: [84, 135, 137], c: [84, 60, 82], d: [121, 184, 206], e: [255, 225, 141], f: [228, 109, 58],
};

// The lid lies thrown back behind the chest, its dark inner side up; below it, the gold rim and the empty inside.
const OPEN = [
  '................',
  '..aaaaaaaaaaaa..',
  '..acccccccccca..',
  '..abccccccccba..',
  '..aabbbbbbbbaa..',
  '.aaaaaaaaaaaaaa.',
  '.aeeeeeeeeeeeea.',
  '.aeaaaaaaaaaaea.',
  '.aeaaaaaaaaaaea.',
  '.aeeeeeeeeeeeea.',
  '.afffffaafffffa.',
  '..acccffffccca..',
  '..abbcccccccca..',
  '..abbbbbbbbbca..',
  '..aaaaaaaaaaaa..',
  '................',
];

const region = process.argv[2];
if (!region) {
  console.error('usage: node tools/art/chest.mjs <region>');
  process.exit(1);
}
const items = path.join(ROOT, 'content', 'art', 'ninja-adventure');
const source = readPng(readFileSync(path.join(items, 'source', 'items', 'LittleTreasureChest.png')));
const graded = readPng(readFileSync(path.join(items, region, 'items', 'LittleTreasureChest.png')));

// Each pack colour as the region's grade drew it, found where the source uses it.
const gradedColour = {};
for (const [letter, rgb] of Object.entries(SOURCE_COLOURS)) {
  for (let i = 0; i < source.data.length; i += 4) {
    if (source.data[i + 3] && rgb.every((v, k) => source.data[i + k] === v)) {
      gradedColour[letter] = [...graded.data.slice(i, i + 4)];
      break;
    }
  }
  if (!gradedColour[letter]) throw new Error(`colour ${letter} is not in the pack's chest`);
}

const sheet = blankImage(TILE * 2, TILE, [0, 0, 0, 0]);
for (let y = 0; y < TILE; y++) {
  for (let x = 0; x < TILE; x++) {
    const from = (y * graded.width + x) * 4;
    sheet.data.set(graded.data.slice(from, from + 4), (y * sheet.width + x) * 4);       // closed, as the pack drew it
    const key = OPEN[y][x];
    if (key === '.') continue;
    const [r, g, b, a] = gradedColour[key];
    const dim = (v) => Math.round(v * OPEN_BRIGHTNESS);
    sheet.data.set([dim(r), dim(g), dim(b), a], (y * sheet.width + TILE + x) * 4);
  }
}
const out = path.join(ROOT, 'content', 'art', 'shardbound', `chest-${region}.png`);
writeFileSync(out, writePng(sheet));
console.log(`wrote ${path.relative(ROOT, out)}`);

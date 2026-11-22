// Draws a numbered, enlarged copy of a tileset so map authors can read tile
// ids: node tools/maps/tileset-index.mjs <tileset.png> <out.png> [tile=16] [scale=3] [col,row,width,height]
// Tile id = row * columns + column, the same local id Tiled uses (gid = firstgid + id).
// The optional last argument draws only that block of tiles (ids stay those of the full sheet).
import { readFileSync, writeFileSync } from 'node:fs';
import { blankImage, readPng, writePng } from './png.mjs';

// 3×5 digit glyphs, one string per row ('1' = ink).
const DIGITS = {
  0: ['111', '101', '101', '101', '111'], 1: ['010', '110', '010', '010', '111'],
  2: ['111', '001', '111', '100', '111'], 3: ['111', '001', '111', '001', '111'],
  4: ['101', '101', '111', '001', '001'], 5: ['111', '100', '111', '001', '111'],
  6: ['111', '100', '111', '101', '111'], 7: ['111', '001', '001', '001', '001'],
  8: ['111', '101', '111', '101', '111'], 9: ['111', '101', '111', '001', '111'],
};

const [input, output, tileArg = '16', scaleArg = '3', blockArg] = process.argv.slice(2);
if (!input || !output) {
  console.error('usage: node tools/maps/tileset-index.mjs <tileset.png> <out.png> [tile=16] [scale=3]');
  process.exit(1);
}
const tile = Number(tileArg);
const scale = Number(scaleArg);
const source = readPng(readFileSync(input));
const sheetColumns = Math.floor(source.width / tile);
const sheetRows = Math.floor(source.height / tile);
const [firstColumn, firstRow, columns, rows] = blockArg ? blockArg.split(',').map(Number) : [0, 0, sheetColumns, sheetRows];
const image = blankImage(columns * tile * scale, rows * tile * scale, [40, 40, 48, 255]);

function put(x, y, [r, g, b, a]) {
  if (x < 0 || y < 0 || x >= image.width || y >= image.height) return;
  const i = (y * image.width + x) * 4;
  const alpha = a / 255;
  image.data[i] = Math.round(r * alpha + image.data[i] * (1 - alpha));
  image.data[i + 1] = Math.round(g * alpha + image.data[i + 1] * (1 - alpha));
  image.data[i + 2] = Math.round(b * alpha + image.data[i + 2] * (1 - alpha));
  image.data[i + 3] = 255;
}

// The tiles, enlarged with nearest-neighbour sampling.
for (let y = 0; y < rows * tile * scale; y++) {
  for (let x = 0; x < columns * tile * scale; x++) {
    const i = ((firstRow * tile + Math.floor(y / scale)) * source.width + firstColumn * tile + Math.floor(x / scale)) * 4;
    put(x, y, [...source.data.subarray(i, i + 4)]);
  }
}

// Grid lines and each tile's id in its top-left corner (1-pixel font scaled by 1).
for (let row = 0; row < rows; row++) {
  for (let column = 0; column < columns; column++) {
    const x0 = column * tile * scale;
    const y0 = row * tile * scale;
    for (let k = 0; k < tile * scale; k++) {
      put(x0 + k, y0, [255, 0, 255, 160]);
      put(x0, y0 + k, [255, 0, 255, 160]);
    }
    const label = String((firstRow + row) * sheetColumns + firstColumn + column);
    for (let x = 0; x < label.length * 4 + 1; x++) for (let y = 0; y < 7; y++) put(x0 + 1 + x, y0 + 1 + y, [0, 0, 0, 200]);
    [...label].forEach((digit, d) => {
      DIGITS[digit].forEach((line, gy) => {
        [...line].forEach((ink, gx) => {
          if (ink === '1') put(x0 + 2 + d * 4 + gx, y0 + 2 + gy, [255, 255, 255, 255]);
        });
      });
    });
  }
}

writeFileSync(output, writePng(image));
console.log(`${input}: ${columns}×${rows} tiles → ${output}`);

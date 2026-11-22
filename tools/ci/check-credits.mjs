// Fails when an asset file has no row in CREDITS.md (V2_SPEC §11): every
// image, audio and font file under content/art/, content/audio/,
// frontend/src/assets/ and frontend/public/ must be covered by a row whose
// first cell names the file or a folder above it ("folder/" or "folder/**").
//
//   node tools/ci/check-credits.mjs
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const ASSET_DIRS = ['content/art', 'content/audio', 'frontend/src/assets', 'frontend/public'];
const ASSET = /\.(png|jpe?g|gif|webp|svg|ogg|mp3|m4a|aac|wav|ttf|otf|woff2?)$/i;

function filesUnder(dir) {
  const absolute = path.join(ROOT, dir);
  if (!existsSync(absolute)) return [];
  return readdirSync(absolute).flatMap((name) => {
    const relative = `${dir}/${name}`;
    return statSync(path.join(ROOT, relative)).isDirectory() ? filesUnder(relative) : [relative];
  });
}

/** The path in the first cell of each table row of CREDITS.md. */
function creditedPaths(markdown) {
  return markdown.split(/\r?\n/)
    .filter((line) => line.startsWith('| `'))
    .map((line) => line.match(/^\| `([^`]+)`/)?.[1])
    .filter(Boolean);
}

/** A folder row ("folder/" or "folder/**") covers everything below it; a file row covers that file. */
function covers(credited, file) {
  const folder = credited.endsWith('/**') ? credited.slice(0, -2) : credited;
  return folder.endsWith('/') ? file.startsWith(folder) : file === credited;
}

const credited = creditedPaths(readFileSync(path.join(ROOT, 'CREDITS.md'), 'utf8'));
const assets = ASSET_DIRS.flatMap(filesUnder).filter((file) => ASSET.test(file));
const missing = assets.filter((file) => !credited.some((entry) => covers(entry, file)));

if (missing.length > 0) {
  for (const file of missing) console.error(`::error file=${file}::${file} has no row in CREDITS.md`);
  process.exit(1);
}
console.log(`CREDITS.md covers all ${assets.length} asset files.`);

// Checks the production frontend build (frontend/dist) on every PR (§4.13, P-36):
//  - the JavaScript the title screen loads and the lazy Phaser chunk stay within
//    their gzip budgets;
//  - the region's maps and art stay within the asset budget, and their decoded
//    images within the texture-memory budget;
//  - the sound effects, the region's music and the battle music stay within the audio budget (P-42, P-48, P-57);
//  - no dev-only code reached the bundle (the inspector, chapter select, /api/debug).
// Budgets come from content/balance.json.
//
//   node tools/ci/check-bundle.mjs [distDir=frontend/dist]
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { gzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const dist = path.resolve(process.argv[2] ?? path.join(ROOT, 'frontend', 'dist'));
const budgets = JSON.parse(readFileSync(path.join(ROOT, 'content', 'balance.json'), 'utf8')).budgets;
const DEV_ONLY = ['/api/debug', 'chapter-select', '"Inspector"'];
const PHASER_CHUNK = /^startWorld-.*\.js$/;
const ART_OR_MAP = /\.(png|tmj)$/;
const EFFECT = /\.wav$/;
const MUSIC = /\.(ogg|m4a)$/;

const assetsDir = path.join(dist, 'assets');
const assets = readdirSync(assetsDir);
const kb = (bytes) => bytes / 1024;
const gzipKb = (file) => kb(gzipSync(readFileSync(path.join(assetsDir, file))).length);
const problems = [];
const report = [];

function check(label, value, limit, unit) {
  report.push(`${label}: ${value.toFixed(1)} ${unit} (budget ${limit} ${unit})`);
  if (value > limit) problems.push(`${label} is ${value.toFixed(1)} ${unit}, over the budget of ${limit} ${unit}`);
}

// The title screen loads the entry script and everything index.html preloads.
const html = readFileSync(path.join(dist, 'index.html'), 'utf8');
const initial = [...html.matchAll(/(?:src|href)="\/assets\/([^"]+\.js)"/g)].map((m) => m[1]);
if (initial.length === 0) problems.push('index.html loads no JavaScript from /assets');
check('Initial JS (title)', initial.reduce((sum, file) => sum + gzipKb(file), 0), budgets.initialJsGzipKb, 'KB gzip');

const phaser = assets.filter((file) => PHASER_CHUNK.test(file));
if (phaser.length !== 1) problems.push(`expected one lazy Phaser chunk (startWorld-*.js), found ${phaser.length}`);
if (phaser.some((file) => initial.includes(file))) problems.push('the Phaser chunk is loaded by the title screen');
check('Phaser chunk (lazy)', phaser.reduce((sum, file) => sum + gzipKb(file), 0), budgets.phaserChunkGzipKb, 'KB gzip');

// Eldmere is the only region so far: every map and art file in the build is its pack.
const pack = assets.filter((file) => ART_OR_MAP.test(file));
check('Region asset pack (Eldmere)', kb(pack.reduce((sum, file) => sum + statSync(path.join(assetsDir, file)).size, 0)),
  budgets.regionAssetsKb, 'KB');
const textureBytes = pack.filter((file) => file.endsWith('.png')).reduce((sum, file) => {
  const png = readFileSync(path.join(assetsDir, file));
  return sum + png.readUInt32BE(16) * png.readUInt32BE(20) * 4;     // IHDR width × height, RGBA
}, 0);
check('Texture memory (Eldmere)', textureBytes / (1024 * 1024), budgets.textureMemoryMb, 'MB');

// What one player downloads: every effect, plus each track in the larger of its two versions (OGG, or the
// AAC fallback, P-48): Eldmere's music and the battle track (P-57). Built files are named <track>-<hash>.<ext>,
// where Vite's hash is 8 characters that may include - and _ (battle-C75rI3V-.ogg).
const sizeOf = (file) => statSync(path.join(assetsDir, file)).size;
const effectFiles = assets.filter((file) => EFFECT.test(file));
const musicFiles = assets.filter((file) => MUSIC.test(file));
const trackOf = (file) => file.replace(/-[A-Za-z0-9_-]{8}\.(ogg|m4a)$/, '');
const largestPerTrack = new Map();
for (const file of musicFiles) largestPerTrack.set(trackOf(file), Math.max(largestPerTrack.get(trackOf(file)) ?? 0, sizeOf(file)));
if (effectFiles.length === 0 || !largestPerTrack.has('eldmere') || !largestPerTrack.has('battle')) {
  problems.push('the build is missing its sound effects, the Eldmere music or the battle music');
}
check('Audio (effects + Eldmere music + battle music)',
  kb(effectFiles.reduce((sum, file) => sum + sizeOf(file), 0) + [...largestPerTrack.values()].reduce((a, b) => a + b, 0)),
  budgets.audioKb, 'KB');

for (const file of assets.filter((f) => f.endsWith('.js'))) {
  const code = readFileSync(path.join(assetsDir, file), 'utf8');
  for (const marker of DEV_ONLY) {
    if (code.includes(marker)) problems.push(`dev-only code (${marker}) is in the production bundle: ${file}`);
  }
}

console.log(report.join('\n'));
if (problems.length > 0) {
  for (const problem of problems) console.error(`::error::${problem}`);
  process.exit(1);
}
console.log('Bundle and asset budgets hold; no dev-only code in the bundle.');

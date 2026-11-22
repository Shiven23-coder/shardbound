// Renders a .tmj map to a PNG for review, approximating the runtime look:
// tile layers in order, actors (with drop shadows) between `decor` and
// `above`, the ambient darkness of a story state, additive warm light from
// `light` objects, optional rain, then an integer upscale and a vignette.
//
//   node tools/maps/render-map.mjs <map.tmj> <out.png> [--scale=3] [--vignette=0.55]
//        [--view=x,y,w,h (tiles)] [--state=storm_night|dawn] [--rain]
//
// Actor sprites come from the art manifest's frames once it exists; until
// then they are given here by NPC id (first frame of each sheet, facing down).
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { blankImage, readPng, writePng } from './png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const ACTORS = {
  player: 'content/art/ninja-adventure/eldmere/actors/Hunter/SpriteSheet.png',
  corvin: 'content/art/ninja-adventure/eldmere/actors/Noble/SpriteSheet.png',
  maren: 'content/art/ninja-adventure/eldmere/actors/OldWoman/SpriteSheet.png',
  brann: 'content/art/ninja-adventure/eldmere/actors/Master/SpriteSheet.png',
};
const ACTOR_SHADOW = 'content/art/ninja-adventure/eldmere/actors/Shadow.png';

const [mapFile, output, ...flags] = process.argv.slice(2);
if (!mapFile || !output) {
  console.error('usage: node tools/maps/render-map.mjs <map.tmj> <out.png> [--scale=3] [--vignette=0.55] [--view=x,y,w,h] [--state=storm_night|dawn] [--rain]');
  process.exit(1);
}
const option = (name, fallback) => flags.find((f) => f.startsWith(`--${name}=`))?.split('=')[1] ?? fallback;
const scale = Number(option('scale', '3'));
const vignette = Number(option('vignette', '0.55'));
const state = option('state', null);
const rain = flags.includes('--rain');

const map = JSON.parse(readFileSync(mapFile, 'utf8'));
const tile = map.tilewidth;
const mapProperty = (name) => map.properties?.find((p) => p.name === name)?.value;
// Ambient level and tint per story state come from the region's lighting in grades.json.
const lighting = JSON.parse(readFileSync(path.join(ROOT, 'content', 'art', 'grades.json'), 'utf8')).lighting[mapProperty('region')];
const time = state ? lighting.states[state] : { ambient: 1, tint: [1, 1, 1] };
const objects = map.layers.find((l) => l.type === 'objectgroup')?.objects ?? [];
const prop = (o, name) => o.properties?.find((p) => p.name === name)?.value;
const tilesets = map.tilesets.map((t) => ({ ...t, png: readPng(readFileSync(path.resolve(path.dirname(mapFile), t.image))) }));
const canvas = blankImage(map.width * tile, map.height * tile, [0, 0, 0, 255]);

function blit(src, sx, sy, w, h, dx, dy) {
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const tx = dx + x;
      const ty = dy + y;
      if (tx < 0 || ty < 0 || tx >= canvas.width || ty >= canvas.height) continue;
      const s = ((sy + y) * src.width + sx + x) * 4;
      const a = src.data[s + 3] / 255;
      if (a === 0) continue;
      const d = (ty * canvas.width + tx) * 4;
      for (let c = 0; c < 3; c++) canvas.data[d + c] = Math.round(src.data[s + c] * a + canvas.data[d + c] * (1 - a));
    }
  }
}

function drawTileLayer(layer) {
  layer.data.forEach((g, i) => {
    if (!g) return;
    const ts = [...tilesets].reverse().find((t) => g >= t.firstgid);
    const id = g - ts.firstgid;
    blit(ts.png, (id % ts.columns) * tile, Math.floor(id / ts.columns) * tile, tile, tile, (i % map.width) * tile, Math.floor(i / map.width) * tile);
  });
}

function drawActors() {
  const actors = [];
  for (const o of objects) {
    if (o.type === 'npc' && ACTORS[prop(o, 'npcId')]) actors.push({ sheet: ACTORS[prop(o, 'npcId')], x: o.x, y: o.y });
    if (o.type === 'spawn_point' && prop(o, 'main')) actors.push({ sheet: ACTORS.player, x: o.x, y: o.y });
  }
  const shadow = readPng(readFileSync(path.join(ROOT, ACTOR_SHADOW)));
  actors.sort((a, b) => a.y - b.y);
  for (const actor of actors) {
    blit(shadow, 0, 0, shadow.width, shadow.height, actor.x + (tile - shadow.width) / 2, actor.y + tile - shadow.height + 2);
    blit(readPng(readFileSync(path.join(ROOT, actor.sheet))), 0, 0, tile, tile, actor.x, actor.y);
  }
}

/**
 * Lighting as at runtime: the scene is multiplied by the ambient level (a
 * dark overlay), and each light adds its colour back with a smooth falloff
 * (an additive light sprite), so pools of warm light stand out of the dark.
 */
function applyLighting() {
  const { ambient } = time;
  const lights = objects.filter((o) => o.type === 'light').map((o) => {
    const hex = prop(o, 'color');
    return {
      x: o.x + tile / 2, y: o.y + tile / 2, radius: prop(o, 'radius') * tile * lighting.lightRadiusScale, intensity: prop(o, 'intensity'),
      color: [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255),
    };
  });
  for (let y = 0; y < canvas.height; y++) {
    for (let x = 0; x < canvas.width; x++) {
      const light = time.tint.map((t) => ambient * t);
      const glow = [0, 0, 0];
      for (const l of lights) {
        const d = Math.hypot(x + 0.5 - l.x, y + 0.5 - l.y) / l.radius;
        if (d >= 1) continue;
        const f = (1 - d) * (1 - d);
        for (let c = 0; c < 3; c++) {
          light[c] += l.color[c] * l.intensity * f * (1 - ambient) * 1.4;
          glow[c] += l.color[c] * l.intensity * f * f * 40;
        }
      }
      const i = (y * canvas.width + x) * 4;
      for (let c = 0; c < 3; c++) canvas.data[i + c] = Math.min(255, Math.round(canvas.data[i + c] * Math.min(light[c], 1.35) + glow[c]));
    }
  }
}

/** Light rain: thin slanted streaks, deterministic so renders are comparable. */
function drawRain() {
  let seed = 1;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  const drops = Math.round((canvas.width * canvas.height) / 700);
  for (let n = 0; n < drops; n++) {
    const x0 = Math.floor(random() * canvas.width);
    const y0 = Math.floor(random() * canvas.height);
    const length = 4 + Math.floor(random() * 4);
    const alpha = 0.22 + random() * 0.18;
    for (let k = 0; k < length; k++) {
      const x = x0 - Math.floor(k / 3);
      const y = y0 + k;
      if (x < 0 || y >= canvas.height) break;
      const i = (y * canvas.width + x) * 4;
      [168, 182, 210].forEach((v, c) => { canvas.data[i + c] = Math.round(v * alpha + canvas.data[i + c] * (1 - alpha)); });
    }
  }
}

for (const layer of map.layers) {
  if (layer.type === 'tilelayer' && layer.name !== 'collision' && layer.name !== 'above') drawTileLayer(layer);
}
drawActors();
const above = map.layers.find((l) => l.name === 'above');
if (above) drawTileLayer(above);
applyLighting();
if (rain) drawRain();

// Camera view (in tiles), integer upscale, then the vignette.
const view = option('view', null);
const [vx, vy, vw, vh] = view ? view.split(',').map((n) => Number(n) * tile) : [0, 0, canvas.width, canvas.height];
const out = blankImage(vw * scale, vh * scale, [0, 0, 0, 255]);
for (let y = 0; y < out.height; y++) {
  for (let x = 0; x < out.width; x++) {
    const s = ((vy + Math.floor(y / scale)) * canvas.width + vx + Math.floor(x / scale)) * 4;
    const nx = (x / out.width) * 2 - 1;
    const ny = (y / out.height) * 2 - 1;
    const edge = Math.max(0, Math.sqrt(nx * nx + ny * ny) - 0.45) / 0.95;
    const dim = 1 - vignette * Math.min(1, edge * edge * 1.6);
    const d = (y * out.width + x) * 4;
    for (let c = 0; c < 3; c++) out.data[d + c] = Math.round(canvas.data[s + c] * dim);
    out.data[d + 3] = 255;
  }
}
writeFileSync(output, writePng(out));
console.log(`rendered ${mapFile} → ${output}`);

// The map kit: what every drafted map is made from, following the map rules
// in V2_SPEC §9. A map is painted as terrain shapes (forest walls, bent
// paths, water, clearings), autotiled from the pack's own pixels (blob.mjs),
// then dressed with deliberate stamp groups, ground detail, drop shadows,
// lights and `reserved` space for later phases.
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { neighbourMask, pick, readBlob } from './blob.mjs';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TILE = 16;

// ---- tilesets, in gid order (columns × rows of 16×16 tiles) ----
const TILESETS = [
  { name: 'floor', image: 'ninja-adventure/eldmere/tilesets/TilesetFloor.png', columns: 22, rows: 26 },
  { name: 'water', image: 'ninja-adventure/eldmere/tilesets/TilesetWater.png', columns: 28, rows: 17 },
  { name: 'detail', image: 'ninja-adventure/eldmere/tilesets/TilesetFloorDetail.png', columns: 16, rows: 5 },
  { name: 'nature', image: 'ninja-adventure/eldmere/tilesets/TilesetNature.png', columns: 24, rows: 21 },
  { name: 'house', image: 'ninja-adventure/eldmere/tilesets/TilesetHouse.png', columns: 33, rows: 23 },
  { name: 'abandoned', image: 'ninja-adventure/eldmere/tilesets/TilesetVillageAbandoned.png', columns: 20, rows: 12 },
  { name: 'shadows', image: 'shardbound/shadows.png', columns: 4, rows: 1 },
  { name: 'props', image: 'shardbound/props.png', columns: 4, rows: 1 },
];
let nextGid = 1;
for (const t of TILESETS) {
  t.firstgid = nextGid;
  nextGid += t.columns * t.rows;
}
const gid = (tileset, id) => TILESETS.find((t) => t.name === tileset).firstgid + id;

// ---- ground: base grass and its autotiled dirt, read from the source art ----
const SOURCE = path.join(ROOT, 'content', 'art', 'ninja-adventure', 'source', 'tilesets');
const LIGHT_DIRT = readBlob(path.join(SOURCE, 'TilesetFloor.png'), { col: 0, row: 7, width: 11, height: 5 }, 264);
const GROUNDS = {
  light: { grass: [264, 264, 264, 264, 264, 264, 268, 268, 265, 264, 264, 268], dirt: LIGHT_DIRT },
  // The dark block (dark dirt on dark grass) repeats the light block's layout
  // 11 columns to the right; its colours are too close to classify by pixels.
  dark: {
    grass: [275, 275, 275, 275, 275, 275, 279, 279, 276, 275, 275, 279],
    dirt: new Map([...LIGHT_DIRT].map(([mask, id]) => [mask, id + 11])),
  },
};
const WATER = readBlob(path.join(SOURCE, 'TilesetWater.png'), { col: 0, row: 6, width: 11, height: 5 }, 308);

/** Deterministic hash noise in [0, 1): the same map on every build. */
export function noise(x, y, seed = 0) {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(seed, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

// ---- stamps: rows of local ids (null = empty); rows < `above` draw over the player ----
// solid: blocking rows; shadow: 'tree' (ellipse under a 2-wide trunk row),
// 'prop' (small round), 'building' (band below).
// A building blocks its whole footprint, roof included, and writes a `building`
// object so the server checks it (P-43); its door is an `interact` object
// placed by the map until interiors exist.
const tree = (rows) => ({ tileset: 'nature', rows, above: 1, solid: [1], shadow: 'tree' });
const prop = (tileset, id) => ({ tileset, rows: [[id]], solid: [0], shadow: 'prop' });
const building = (tileset, rows, above, shadow = 'building') =>
  ({ tileset, rows, above, solid: rows.map((_, dy) => dy), building: true, shadow });
export const STAMPS = {
  // buildings
  cottage: building('house', [[256, 257, 258, 259], [289, 290, 291, 292], [322, 323, 324, 325], [421, 422, 423, 424], [454, 455, 456, 457]], 3),
  houseRed: building('house', [[12, 13, 14, 15], [45, 46, 47, 48], [78, 79, 80, 81]], 1),
  houseBrown: building('house', [[0, 1, 2, 3], [33, 34, 35, 36], [66, 67, 68, 69]], 1),
  houseTan: building('house', [[4, 5, 6, 7], [37, 38, 39, 40], [70, 71, 72, 73]], 1),
  houseWood: building('house', [[26, 27, 28], [59, 60, 61], [92, 93, 94]], 1),
  forge: building('house', [[23, 24, 25], [56, 57, 58], [89, 90, 91]], 1),
  mill: building('house', [[null, 488, 489, null], [520, 521, 522, 523], [553, 554, 555, 556], [586, 587, 588, 589], [619, 620, 621, 622]], 3),
  kiln: building('house', [[392, 393], [425, 426]], 1, 'tree'),
  tent: building('house', [[379, 380], [412, 413]], 1, 'tree'),
  weaponRack: { tileset: 'house', rows: [[377, 378], [410, 411]], above: 1, solid: [1], shadow: 'tree' },
  ironGate: { tileset: 'house', rows: [[374, 375], [407, 408]], above: 1, solid: [1] },
  ironFence: { tileset: 'house', rows: [[340]], solid: [0] },
  // ruins
  watchtower: building('abandoned', [[14, 15, 16], [34, 35, 36], [54, 55, 56], [74, 75, 76], [94, 95, 96]], 2),
  ruinedHouse: building('abandoned', [[0, 1, 2, 3], [20, 21, 22, 23], [40, 41, 42, 43]], 1),
  brokenWall: { tileset: 'abandoned', rows: [[77, 78, 79], [97, 98, 99]], above: 1, solid: [1], shadow: 'building' },
  pillar: { tileset: 'abandoned', rows: [[62], [82], [102]], above: 2, solid: [2], shadow: 'prop' },
  pillarBroken: { tileset: 'abandoned', rows: [[63], [83], [103]], above: 2, solid: [2], shadow: 'prop' },
  statue: { tileset: 'abandoned', rows: [[60, 61], [80, 81], [100, 101]], above: 2, solid: [2], shadow: 'tree' },
  rubble: prop('abandoned', 86),
  rubble2: prop('abandoned', 87),
  rubble3: prop('abandoned', 88),
  rubble4: prop('abandoned', 89),
  oldWell: prop('abandoned', 85),
  // trees and rocks
  pine: tree([[2, 3], [26, 27]]),
  roundTree: tree([[16, 17], [40, 41]]),
  paleTree: tree([[0, 1], [24, 25]]),
  deadTree: tree([[4, 5], [28, 29]]),
  canopy: { tileset: 'nature', rows: [[2, 3]], above: 1, solid: [] },
  boulders: { tileset: 'nature', rows: [[null, 137, 138, null], [160, 161, 162, 163], [184, 185, 186, 187]], above: 1, solid: [1, 2], shadow: 'building' },
  boulder: { tileset: 'nature', rows: [[208, 209], [232, 233]], above: 1, solid: [1], shadow: 'tree' },
  rock: prop('nature', 234),
  rockBrown: prop('nature', 231),
  stump: prop('nature', 196),
  log: prop('nature', 197),
  shrub: prop('nature', 241),
  shrub2: prop('nature', 246),
  // village props
  fence: { tileset: 'house', rows: [[176]], solid: [0] },
  fenceLeft: { tileset: 'house', rows: [[174]], solid: [0] },
  fenceRight: { tileset: 'house', rows: [[179]], solid: [0] },
  sign: prop('abandoned', 29),
  barrel: prop('house', 511),
  barrelOpen: prop('house', 512),
  anvil: prop('house', 524),
  sacks: prop('house', 525),
  basketFish: prop('house', 445),
  basketGreens: prop('house', 446),
  basketApples: prop('house', 447),
  crates: prop('house', 559),
  // Shardbound's own props
  lanternPost: { tileset: 'props', rows: [[0], [1]], above: 1, solid: [1], shadow: 'prop' },
  millstone: prop('props', 3),
};

// Ground detail (never blocks), by theme: [tileset, local id].
const DETAIL = {
  tufts: [['detail', 34], ['detail', 32], ['detail', 35], ['detail', 33]],
  flowers: [['detail', 37], ['detail', 37], ['detail', 35], ['detail', 32]],
  debris: [['detail', 5], ['detail', 6], ['detail', 7], ['detail', 0], ['detail', 1]],
  grit: [['detail', 3], ['detail', 3], ['detail', 16]],
  grim: [['detail', 13], ['detail', 14]],
  stones: [['detail', 16], ['detail', 3], ['detail', 15]],
};

// =====================================================================
// Terrain painting
// =====================================================================

export function makeTerrain(width, height, value = 'grass') {
  return Array.from({ length: height }, () => new Array(width).fill(value));
}

/** The terrain kind at (x, y); cells outside count as their nearest edge cell. */
function at(grid, x, y) {
  return grid[Math.min(grid.length - 1, Math.max(0, y))][Math.min(grid[0].length - 1, Math.max(0, x))];
}

function distanceToSegment(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax;
  const dy = by - ay;
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1)));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

/** Paints every cell whose centre lies within `half` of the polyline. */
export function paintPath(grid, points, half, value) {
  grid.forEach((row, y) => row.forEach((_, x) => {
    for (let i = 0; i < points.length - 1; i++) {
      if (distanceToSegment(x + 0.5, y + 0.5, points[i], points[i + 1]) <= half) row[x] = value;
    }
  }));
}

/** Paints an ellipse; with `onlyOn`, only over cells of those kinds. */
export function paintEllipse(grid, cx, cy, rx, ry, value, onlyOn = null) {
  grid.forEach((row, y) => row.forEach((cell, x) => {
    if (((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1 && (onlyOn === null || onlyOn.includes(cell))) row[x] = value;
  }));
}

export function paintRect(grid, x0, y0, width, height, value) {
  for (let y = Math.max(0, y0); y < Math.min(grid.length, y0 + height); y++) {
    for (let x = Math.max(0, x0); x < Math.min(grid[0].length, x0 + width); x++) grid[y][x] = value;
  }
}

/** Forest walls along the map edges, `depth` tiles deep plus up to `bulge` more. */
export function forestBorder(grid, depth, bulge, seed) {
  const height = grid.length;
  const width = grid[0].length;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const extra = noise(Math.floor(x / 3), Math.floor(y / 3), seed) * bulge;
      if (y < depth + extra || y >= height - depth - extra || x < depth + 1 + extra || x >= width - depth - extra) grid[y][x] = 'forest';
    }
  }
}

/**
 * Rounds off a painted terrain so autotiles never need single-tile notches
 * or spikes: a cell of `kind` with at most one such orthogonal neighbour
 * reverts to `background`, and a background cell with three or more becomes `kind`.
 */
export function smooth(grid, kind, background, passes = 2) {
  for (let pass = 0; pass < passes; pass++) {
    const next = grid.map((row) => [...row]);
    grid.forEach((row, y) => row.forEach((cell, x) => {
      const count = [at(grid, x, y - 1), at(grid, x + 1, y), at(grid, x, y + 1), at(grid, x - 1, y)].filter((c) => c === kind).length;
      if (cell === kind && count <= 1) next[y][x] = background;
      if (cell === background && count >= 3) next[y][x] = kind;
    }));
    next.forEach((row, y) => { grid[y] = row; });
  }
}

/**
 * Covers forest cells with overlapping trees on a staggered lattice so the
 * edges read as a dense wall (map rule 1). `pickTree(x, y)` chooses each kind.
 */
export function forestFill(grid, pickTree) {
  const height = grid.length;
  const width = grid[0].length;
  const isForest = (x, y) => x >= -1 && x <= width && y >= 0 && y < height && at(grid, x, y) === 'forest';
  const trees = [];
  for (let y = -1; y < height; y++) {
    const offset = Math.abs(y) % 2;
    for (let x = -offset; x < width; x += 2) {
      // The trunk row must be forest, so trees never stand on paths or clearings.
      if (isForest(x, y + 1) && isForest(x + 1, y + 1)) trees.push([pickTree(x, y), x, y]);
    }
  }
  return trees;
}

// =====================================================================
// Ground detail
// =====================================================================

/** Adds `theme` detail inside a circle. */
export function cluster(details, theme, cx, cy, radius, density, seed) {
  for (let y = Math.floor(cy - radius); y <= cy + radius; y++) {
    for (let x = Math.floor(cx - radius); x <= cx + radius; x++) {
      if (Math.hypot(x - cx, y - cy) <= radius && noise(x, y, seed) < density) details.push([theme, x, y]);
    }
  }
}

/**
 * Debris and grit where paths meet grass, never mid-path (readability):
 * `verge` detail on grass cells beside a path, `edge` detail on the path's
 * own border cells, with double density at corners.
 */
export function pathEdges(details, grid, { verge, edge, density, seed }) {
  const ortho = [[0, -1], [1, 0], [0, 1], [-1, 0]];
  grid.forEach((row, y) => row.forEach((cell, x) => {
    const dirtAround = ortho.filter(([dx, dy]) => at(grid, x + dx, y + dy) === 'dirt').length;
    const roll = noise(x, y, seed);
    if (cell === 'grass' && dirtAround > 0 && verge) {
      if (roll < density * (dirtAround >= 2 ? 2 : 1)) details.push([verge, x, y]);
    } else if (cell === 'dirt' && dirtAround < 4 && edge) {
      if (roll < density * (dirtAround <= 2 ? 2 : 1)) details.push([edge, x, y]);
    }
  }));
}

// =====================================================================
// Assembly
// =====================================================================

function tileLayer(id, name, width, height, data, extra = {}) {
  return { id, name, type: 'tilelayer', x: 0, y: 0, width, height, opacity: 1, visible: true, data, ...extra };
}

/**
 * Builds a Tiled map. `terrain` cells are 'grass', 'dirt', 'water' or
 * 'forest' (grass under trees); `ground` picks the light or dark grass set.
 * Objects are given in tiles: { name, type, x, y, w?, h?, props? }.
 * `obstacleStamps` are drawn in the `obstacles` layer, which the client hides
 * with their obstacle; they add no collision, as the `obstacle` object blocks
 * while active (P-45).
 */
export function build({
  width, height, ground = 'light', terrain, forestTrees = [], stamps, obstacleStamps = [], details, objects, properties,
}) {
  const index = (x, y) => y * width + x;
  const inside = (x, y) => x >= 0 && y >= 0 && x < width && y < height;
  const layer = () => new Array(width * height).fill(0);
  const layers = {
    ground: layer(), detail: layer(), shadows: layer(), decor: layer(), obstacles: layer(), above: layer(), collision: layer(),
  };
  const buildings = [];
  const { grass, dirt } = GROUNDS[ground];

  const is = (kind) => (x, y) => at(terrain, x, y) === kind;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const cell = terrain[y][x];
      let g = gid('floor', grass[Math.floor(noise(x, y, 1) * grass.length)]);
      if (cell === 'dirt') g = gid('floor', pick(dirt, neighbourMask(is('dirt'), x, y)));
      if (cell === 'water') g = gid('water', pick(WATER, neighbourMask(is('water'), x, y)));
      layers.ground[index(x, y)] = g;
      if (cell === 'water' || cell === 'forest') layers.collision[index(x, y)] = 1;
    }
  }

  const place = ([name, x0, y0]) => {
    const stamp = STAMPS[name];
    if (!stamp) throw new Error(`unknown stamp ${name}`);
    stamp.rows.forEach((row, dy) => row.forEach((id, dx) => {
      const x = x0 + dx;
      const y = y0 + dy;
      if (!inside(x, y)) return;
      if (stamp.solid.includes(dy)) layers.collision[index(x, y)] = 1;
      if (id === null) return;
      const target = stamp.above !== undefined && dy < stamp.above ? layers.above : layers.decor;
      target[index(x, y)] = gid(stamp.tileset, id);
    }));
    if (stamp.building) {
      buildings.push({ name: `${name}_${x0}_${y0}`, type: 'building', x: x0, y: y0, w: stamp.rows[0].length, h: stamp.rows.length });
    }
    // Drop shadows (map rule 5).
    const base = y0 + stamp.rows.length - 1;
    const shade = (x, y, id) => { if (inside(x, y)) layers.shadows[index(x, y)] = gid('shadows', id); };
    if (stamp.shadow === 'tree') {
      shade(x0, base, 0);
      shade(x0 + 1, base, 1);
    } else if (stamp.shadow === 'prop') {
      shade(x0, base, 2);
    } else if (stamp.shadow === 'building') {
      for (let dx = 0; dx < stamp.rows[0].length; dx++) shade(x0 + dx, base + 1, 3);
    }
  };
  // Forest walls first, so the deliberate groups draw over them.
  forestTrees.forEach(place);
  stamps.forEach(place);
  for (const [name, x0, y0] of obstacleStamps) {
    const stamp = STAMPS[name];
    if (!stamp) throw new Error(`unknown stamp ${name}`);
    stamp.rows.forEach((row, dy) => row.forEach((id, dx) => {
      if (id !== null && inside(x0 + dx, y0 + dy)) layers.obstacles[index(x0 + dx, y0 + dy)] = gid(stamp.tileset, id);
    }));
  }

  // Ground detail, only on open ground.
  for (const [theme, x, y] of details) {
    if (!inside(x, y) || layers.decor[index(x, y)] || layers.obstacles[index(x, y)] || layers.collision[index(x, y)]) continue;
    const options = DETAIL[theme];
    const [tileset, id] = options[Math.floor(noise(x, y, 7) * options.length)];
    layers.detail[index(x, y)] = gid(tileset, id);
  }

  // Map edges block, except under exits.
  for (let x = 0; x < width; x++) {
    layers.collision[index(x, 0)] = 1;
    layers.collision[index(x, height - 1)] = 1;
  }
  for (let y = 0; y < height; y++) {
    layers.collision[index(0, y)] = 1;
    layers.collision[index(width - 1, y)] = 1;
  }
  for (const o of objects.filter((o) => o.type === 'exit')) {
    for (let dy = 0; dy < (o.h ?? 1); dy++) for (let dx = 0; dx < (o.w ?? 1); dx++) layers.collision[index(o.x + dx, o.y + dy)] = 0;
  }

  let objectId = 1;
  const propertyType = (value) => (typeof value === 'boolean' ? 'bool' : typeof value === 'number' ? 'float' : 'string');
  const tiledProperties = (props) => Object.entries(props ?? {}).map(([name, value]) => ({ name, type: propertyType(value), value }));
  const tiledObjects = [...objects, ...buildings].map((o) => ({
    id: objectId++, name: o.name, type: o.type, x: o.x * TILE, y: o.y * TILE,
    width: (o.w ?? 1) * TILE, height: (o.h ?? 1) * TILE, rotation: 0, visible: true,
    properties: tiledProperties(o.props),
  }));
  const drawn = ['ground', 'detail', 'shadows', 'decor', 'obstacles', 'above'];
  return {
    type: 'map', version: '1.10', tiledversion: '1.11.0', orientation: 'orthogonal', renderorder: 'right-down',
    width, height, tilewidth: TILE, tileheight: TILE, infinite: false,
    nextlayerid: drawn.length + 3, nextobjectid: objectId,
    properties: tiledProperties(properties),
    layers: [
      ...drawn.map((name, i) => tileLayer(i + 1, name, width, height, layers[name])),
      tileLayer(drawn.length + 1, 'collision', width, height, layers.collision.map((s) => (s ? gid('floor', 177) : 0)), { visible: false }),
      { id: drawn.length + 2, name: 'objects', type: 'objectgroup', x: 0, y: 0, opacity: 1, visible: true, draworder: 'topdown', objects: tiledObjects },
    ],
    tilesets: TILESETS.map((t) => ({
      firstgid: t.firstgid, name: t.name, image: `../art/${t.image}`,
      imagewidth: t.columns * TILE, imageheight: t.rows * TILE, tilewidth: TILE, tileheight: TILE,
      columns: t.columns, tilecount: t.columns * t.rows, margin: 0, spacing: 0,
    })),
  };
}

/**
 * Checks what MapValidator will check at startup, so a draft never ships
 * broken: every spawn, exit, NPC, interact, trigger, obstacle, creep and
 * reserved spot is on or next to a cell reachable from the main spawn (with
 * obstacles open), and every building footprint blocks.
 */
export function checkReachable(map) {
  const collision = map.layers.find((l) => l.name === 'collision').data;
  const objects = map.layers.find((l) => l.type === 'objectgroup').objects;
  for (const b of objects.filter((o) => o.type === 'building')) {
    for (let y = b.y / TILE; y < (b.y + b.height) / TILE; y++) {
      for (let x = b.x / TILE; x < (b.x + b.width) / TILE; x++) {
        if (!collision[y * map.width + x]) throw new Error(`building ${b.name} is open at ${x},${y}`);
      }
    }
  }
  const { width, height } = map;
  const main = objects.find((o) => o.type === 'spawn_point' && o.properties.some((p) => p.name === 'main' && p.value));
  if (!main) throw new Error('no main spawn_point');
  const seen = new Set();
  const queue = [[main.x / TILE, main.y / TILE]];
  while (queue.length) {
    const [x, y] = queue.shift();
    const k = y * width + x;
    if (x < 0 || y < 0 || x >= width || y >= height || seen.has(k) || collision[k]) continue;
    seen.add(k);
    queue.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
  }
  const unreachable = objects.filter((o) => !['light', 'effect', 'prop', 'building'].includes(o.type)).filter((o) => {
    const cells = [];
    for (let dy = 0; dy < o.height / TILE; dy++) for (let dx = 0; dx < o.width / TILE; dx++) cells.push([o.x / TILE + dx, o.y / TILE + dy]);
    return !cells.some(([x, y]) => [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => seen.has((y + dy) * width + x + dx)));
  });
  if (unreachable.length) throw new Error(`unreachable: ${unreachable.map((o) => o.name).join(', ')}`);
}

export function writeMap(name, map) {
  checkReachable(map);
  const outDir = path.join(ROOT, 'content', 'maps');
  mkdirSync(outDir, { recursive: true });
  writeFileSync(path.join(outDir, `${name}.tmj`), `${JSON.stringify(map, null, 1)}\n`);
  console.log(`wrote content/maps/${name}.tmj (${map.width}×${map.height})`);
}

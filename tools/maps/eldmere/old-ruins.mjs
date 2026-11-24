// Old Ruins (28 × 20): where the Heartshard fell.
//  - the crater: scorched earth, a ring of rubble, the warm crystal and its steam
//  - the broken watchtower to the west, with fallen pillars and a wall
//  - the north rubble, blocking the way to the Ashen Herald's arena (an obstacle, opened in V7)
//  - the east: a toppled statue and a ruined house; the Ash Wisp and the Barrow Skull from dawn (P-64), the main-path chest (V6a, P-65)
import {
  build, cluster, forestBorder, forestFill, makeTerrain, noise, paintEllipse, paintPath, pathEdges, smooth,
} from '../kit.mjs';

export default function oldRuins() {
  const W = 28;
  const H = 20;
  const terrain = makeTerrain(W, H);
  forestBorder(terrain, 2, 2, 21);
  paintEllipse(terrain, 14, 10, 10.5, 7, 'grass'); // the ruins' clearing
  // The path up from the lane, around the crater, and on to the rubble.
  paintPath(terrain, [[13.9, 21], [13.6, 16], [12, 13.5]], 1.0, 'dirt');
  paintPath(terrain, [[16.5, 13.4], [19, 11], [17.5, 6], [14.5, 4.5], [14.5, -1]], 0.9, 'dirt');
  paintEllipse(terrain, 14, 9.5, 3.6, 2.6, 'dirt'); // the crater's scorched floor
  smooth(terrain, 'dirt', 'grass');

  const forestTrees = forestFill(terrain, (x, y) => (noise(x, y, 22) < 0.6 ? 'deadTree' : 'pine'));

  const stamps = [
    // the crater: rubble thrown around the rim (the Heartshard is a prop object below)
    ['boulder', 9, 7], ['rubble', 11, 12], ['rubble3', 17, 12], ['rock', 10, 10], ['rubble2', 18, 8],
    // the broken watchtower and what fell from it
    ['watchtower', 4, 4], ['pillar', 8, 4], ['pillarBroken', 3, 10], ['brokenWall', 5, 11], ['rubble4', 8, 9],
    // the north rubble, blocking the way to the arena
    ['boulders', 13, 0], ['rubble', 12, 3], ['rubble2', 17, 3], ['rubble4', 16, 2],
    // the east: a toppled statue and a ruined house
    ['statue', 21, 5], ['pillarBroken', 23, 9], ['ruinedHouse', 19, 13], ['rubble3', 24, 12],
  ];

  const details = [];
  pathEdges(details, terrain, { verge: 'stones', edge: 'grit', density: 0.07, seed: 23 });
  cluster(details, 'grim', 6, 13.5, 1.2, 0.3, 24);
  cluster(details, 'tufts', 21, 10, 2, 0.35, 25);
  cluster(details, 'tufts', 6, 15, 2, 0.3, 26);

  return build({
    width: W,
    height: H,
    ground: 'dark',
    terrain,
    forestTrees,
    stamps,
    details,
    properties: { area: 'eldmere_old_ruins', region: 'eldmere' },
    objects: [
      { name: 'from_lane', type: 'spawn_point', x: 13, y: 18, props: { main: true } },
      { name: 'to_lane', type: 'exit', x: 13, y: 19, w: 2, h: 1, props: { toArea: 'eldmere_cottage_lane', toSpawn: 'from_ruins' } },
      { name: 'crater_crystal', type: 'interact', x: 14, y: 9, props: { dialogue: 'crater_crystal' } },
      { name: 'ruins_rubble', type: 'obstacle', x: 12, y: 1, w: 6, h: 3, props: { dialogue: 'ruins_rubble', opensIn: 'V7' } },
      { name: 'crater_steam', type: 'effect', x: 12, y: 8, w: 5, h: 3, props: { effect: 'steam' } },
      { name: 'heartshard', type: 'prop', x: 14, y: 9, props: { sprite: 'props:2', hideIf: 'dawn' } },
      { name: 'heartshard_glow', type: 'light', x: 14, y: 9, props: { radius: 3.4, color: '#8cf0a8', intensity: 0.9, hideIf: 'dawn' } },
      { name: 'arena_way', type: 'reserved', x: 13, y: 1, w: 4, h: 2, props: { for: 'exit', phase: 'V7' } },
      // from dawn only (P-64): the storm-night walk for the Heartshard stays a level-1 walk
      { name: 'ruins_creep_a', type: 'creep_spawn', x: 21, y: 9, props: { template: 'ash_wisp', levelMin: 3, levelMax: 4, aggroRadius: 2, showIf: 'dawn' } },
      { name: 'ruins_creep_b', type: 'creep_spawn', x: 7, y: 14, props: { template: 'barrow_skull', levelMin: 4, levelMax: 4, aggroRadius: 1, showIf: 'dawn' } },
      // the main-path chest (P-65): in view on the way out with the Heartshard
      { name: 'ruins_chest', type: 'chest', x: 24, y: 15,
        props: { item: 'woodcutter_hatchet', qty: 1, flag: 'chest_ruins', fallbackLevel: 3, showIf: 'dawn' } },
    ],
  });
}

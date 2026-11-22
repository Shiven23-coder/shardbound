// Cottage Lane (32 × 22): home, the storm, the road out.
//  - home: the cottage (its door barred against the storm) and its fenced garden, warm window and door light
//  - the north path to the Old Ruins
//  - the junction: reserved for Eldmere's first Lantern (V5)
//  - Corvin's stop on the east lane, under his lantern
//  - the flooded west road toward the Whispering Wood: dead trees, water, a sign; the flood
//    closes the way until the Wood opens (an obstacle, P-45)
//  - the south meadow: reserved for two creeps (V4)
//  - a secret clearing behind the south-east tree wall: reserved chest
import {
  build, cluster, forestBorder, forestFill, makeTerrain, noise, paintEllipse, paintPath, paintRect, pathEdges, smooth,
} from '../kit.mjs';

export default function cottageLane() {
  const W = 32;
  const H = 22;
  const terrain = makeTerrain(W, H);
  forestBorder(terrain, 2, 2, 3);
  paintEllipse(terrain, 7.5, 6.5, 6, 4.2, 'grass'); // home ground
  paintRect(terrain, 24, 15, W - 24, H - 15, 'forest'); // the south-east woods…
  paintEllipse(terrain, 28, 18.5, 2.3, 1.5, 'grass'); // …hide a clearing
  // The lane bends gently from the flooded west edge to the Square in the east.
  paintPath(terrain, [[-1, 12.5], [6, 12.8], [11, 11.7], [16, 11.8], [20, 12.4], [25, 12.6], [29, 11.4], [33, 11.1]], 1.25, 'dirt');
  // The north path to the Old Ruins, with a wide junction.
  paintPath(terrain, [[20, 12], [21.3, 8.5], [19.7, 4.5], [20.1, 1], [20.1, -1]], 1.1, 'dirt');
  paintEllipse(terrain, 20, 12.3, 2.8, 2.0, 'dirt');
  smooth(terrain, 'dirt', 'grass');
  paintPath(terrain, [[7.5, 9.6], [7.5, 11.5]], 0.5, 'dirt'); // the door path, one tile wide
  paintPath(terrain, [[25.5, 14], [25.5, 18]], 0.5, 'grass'); // the hidden way into the clearing
  // Flood water pressing in on both sides of the west road, then rain puddles.
  paintEllipse(terrain, 1.8, 9.2, 3.6, 1.9, 'water', ['grass', 'forest']);
  paintEllipse(terrain, 2.4, 15.6, 3.8, 2.1, 'water', ['grass', 'forest']);
  paintRect(terrain, 0, 11, 3, 3, 'water'); // the flood over the road itself
  smooth(terrain, 'water', 'grass');
  paintEllipse(terrain, 12.8, 15.0, 2.0, 1.25, 'water', ['grass']);
  paintEllipse(terrain, 20.8, 15.0, 2.0, 1.25, 'water', ['grass']);

  const forestTrees = forestFill(terrain, (x, y) => {
    if (x < 9) return noise(x, y, 11) < 0.55 ? 'deadTree' : 'pine'; // the west foreshadows the Wood
    const n = noise(x, y, 12);
    return n < 0.7 ? 'pine' : n < 0.88 ? 'roundTree' : 'deadTree';
  });

  const stamps = [
    // home: the cottage and a fenced garden, with a gap at the door path
    ['cottage', 6, 4],
    ['fenceLeft', 3, 9], ['fence', 4, 9], ['fence', 5, 9], ['fenceRight', 6, 9],
    ['fenceLeft', 8, 9], ['fence', 9, 9], ['fence', 10, 9], ['fenceRight', 11, 9],
    ['shrub', 4, 7], ['shrub2', 11, 7], ['stump', 12, 6], ['log', 3, 6],
    // a grove north-east of the cottage, with a walkable gap to the path
    ['roundTree', 13, 3], ['paleTree', 15, 4], ['roundTree', 14, 6],
    // east of the north path: a pair with a rock, leaving the bend open
    ['roundTree', 23, 4], ['pine', 25, 5], ['rock', 24, 7],
    // Corvin's stop: the lantern post, stumps and a log, sheltered by a tree
    ['lanternPost', 27, 8], ['stump', 25, 10], ['log', 26, 10], ['rockBrown', 29, 10], ['roundTree', 28, 6],
    // the flooded west road: dead trees close in, the warning sign
    ['deadTree', 8, 14], ['deadTree', 4, 16], ['sign', 6, 11], ['log', 10, 15],
    // south meadow edge groups; the middle stays open for creeps
    ['roundTree', 12, 16], ['pine', 14, 17], ['shrub', 16, 16],
    ['deadTree', 20, 16], ['shrub2', 22, 17],
    // canopies hiding the gap into the secret clearing
    ['canopy', 24, 15], ['canopy', 25, 16],
  ];

  // Ground detail (map rule 3); storm debris only along path edges and corners.
  const details = [];
  cluster(details, 'flowers', 5, 6.5, 1.6, 0.5, 1); // the garden
  cluster(details, 'flowers', 10, 7, 1.4, 0.5, 2);
  cluster(details, 'tufts', 15, 9, 2, 0.35, 3);
  cluster(details, 'tufts', 17, 17.5, 2.5, 0.3, 4);
  cluster(details, 'tufts', 23, 2.8, 1.5, 0.35, 6);
  pathEdges(details, terrain, { verge: 'debris', edge: 'grit', density: 0.07, seed: 7 });
  details.push(['grim', 6, 13]); // something the flood washed up
  cluster(details, 'flowers', 28, 18.5, 1.6, 0.45, 14); // the secret clearing

  return build({
    width: W,
    height: H,
    terrain,
    forestTrees,
    stamps,
    details,
    properties: { area: 'eldmere_cottage_lane', region: 'eldmere' },
    objects: [
      { name: 'home', type: 'spawn_point', x: 7, y: 10, props: { main: true } },
      { name: 'from_ruins', type: 'spawn_point', x: 20, y: 1 },
      { name: 'from_square', type: 'spawn_point', x: 30, y: 11 },
      { name: 'to_ruins', type: 'exit', x: 19, y: 0, w: 2, h: 1, props: { toArea: 'eldmere_old_ruins', toSpawn: 'from_lane' } },
      { name: 'to_square', type: 'exit', x: 31, y: 10, w: 1, h: 2, props: { toArea: 'eldmere_square', toSpawn: 'from_lane' } },
      { name: 'corvin', type: 'npc', x: 26, y: 9, props: { npcId: 'corvin', dialogue: 'corvin_storm', showIf: 'storm_night' } },
      { name: 'forest_sign', type: 'interact', x: 6, y: 11, props: { dialogue: 'forest_sign' } },
      { name: 'cottage_door', type: 'interact', x: 7, y: 8, props: { dialogue: 'cottage_door' } },
      { name: 'flooded_road', type: 'obstacle', x: 1, y: 11, w: 2, h: 3, props: { dialogue: 'flooded_road', opensIn: 'WW' } },
      // warm light (map rule 6)
      { name: 'cottage_window', type: 'light', x: 8, y: 7, props: { radius: 2.4, color: '#ffb35c', intensity: 0.85 } },
      { name: 'cottage_door', type: 'light', x: 7, y: 9, props: { radius: 3.2, color: '#ff9a3c', intensity: 0.8 } },
      { name: 'corvin_lantern', type: 'light', x: 27, y: 8, props: { radius: 3.4, color: '#ffc46b', intensity: 1.0 } },
      // purpose kept for later phases (map rule 2)
      { name: 'first_lantern', type: 'reserved', x: 22, y: 13, props: { for: 'lantern', phase: 'V5' } },
      { name: 'meadow_creep_a', type: 'reserved', x: 17, y: 17, props: { for: 'creep', phase: 'V4' } },
      { name: 'meadow_creep_b', type: 'reserved', x: 9, y: 17, props: { for: 'creep', phase: 'V4' } },
      { name: 'clearing_chest', type: 'reserved', x: 28, y: 18, props: { for: 'chest', phase: 'V5' } },
      { name: 'hidden_gap', type: 'reserved', x: 25, y: 15, w: 1, h: 3, props: { for: 'secret' } },
    ],
  });
}

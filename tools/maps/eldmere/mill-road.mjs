// Mill Road (30 × 22): the old mill and the road south.
//  - the mill and its millstone
//  - the fenced field: the Furrow Mole and the Hedge Adder (P-64)
//  - the stream and the flooded ford, with its sign and lantern; the flood closes
//    the road south until the Wood opens (an obstacle, P-45)
//  - the south edge: dead trees toward the Whispering Wood
//  - a nook by the stream: space for a chest (V6)
import {
  build, cluster, forestBorder, forestFill, makeTerrain, noise, paintEllipse, paintPath, pathEdges, smooth,
} from '../kit.mjs';

export default function millRoad() {
  const W = 30;
  const H = 22;
  const terrain = makeTerrain(W, H);
  forestBorder(terrain, 2, 2, 41);
  paintEllipse(terrain, 15, 9.5, 12, 7, 'grass'); // the mill's open ground
  paintEllipse(terrain, 14, 17.5, 9, 2.4, 'grass'); // the stream's banks
  // The road from the Square down to the ford and on to the forest.
  paintPath(terrain, [[15.5, -1], [15.5, 4], [14, 9], [14.5, 14], [13, 18], [13.5, 23]], 1.1, 'dirt');
  paintPath(terrain, [[14, 10.5], [8.5, 10.5]], 0.6, 'dirt'); // the track to the mill
  smooth(terrain, 'dirt', 'grass');
  // The swollen stream crosses the map and drowns the ford.
  paintPath(terrain, [[-1, 16], [7, 16.5], [20, 15.6], [31, 16.2]], 1.0, 'water');
  smooth(terrain, 'water', 'grass');
  paintEllipse(terrain, 19, 12.8, 1.7, 1.05, 'water', ['grass']); // a flood puddle by the ford
  smooth(terrain, 'water', 'grass', 1);

  const forestTrees = forestFill(terrain, (x, y) => {
    if (y > 15) return noise(x, y, 42) < 0.6 ? 'deadTree' : 'pine'; // the south foreshadows the Wood
    const n = noise(x, y, 43);
    return n < 0.65 ? 'pine' : n < 0.9 ? 'roundTree' : 'deadTree';
  });

  const stamps = [
    // the mill, its millstone and stores
    ['mill', 4, 6], ['millstone', 8, 10], ['sacks', 3, 11], ['crates', 8, 8], ['barrel', 9, 8],
    // the fenced field east of the road
    ['fenceLeft', 19, 5], ['fence', 20, 5], ['fence', 21, 5], ['fence', 22, 5], ['fence', 23, 5], ['fenceRight', 24, 5],
    ['fenceLeft', 19, 11], ['fence', 20, 11], ['fenceRight', 21, 11], ['fenceLeft', 23, 11], ['fenceRight', 24, 11],
    // groups along the road
    ['roundTree', 10, 2], ['paleTree', 18, 2], ['shrub', 12, 4],
    ['roundTree', 24, 13], ['shrub2', 26, 12],
    // the ford: the warning sign and a lantern
    ['sign', 12, 13], ['lanternPost', 16, 11],
    // the south bank: dead trees close in
    ['deadTree', 6, 18], ['deadTree', 19, 18], ['log', 17, 19],
  ];

  const details = [];
  pathEdges(details, terrain, { verge: 'debris', edge: 'grit', density: 0.06, seed: 44 });
  cluster(details, 'tufts', 21.5, 8, 2.6, 0.55, 45); // the field's tall grass
  cluster(details, 'tufts', 5, 13, 1.6, 0.35, 46);
  cluster(details, 'flowers', 26, 9, 1.4, 0.4, 47);
  details.push(['grim', 11, 17]);

  return build({
    width: W,
    height: H,
    terrain,
    forestTrees,
    stamps,
    details,
    properties: { area: 'eldmere_mill_road', region: 'eldmere' },
    objects: [
      { name: 'from_square', type: 'spawn_point', x: 15, y: 1, props: { main: true } },
      { name: 'to_square', type: 'exit', x: 14, y: 0, w: 3, h: 1, props: { toArea: 'eldmere_square', toSpawn: 'from_mill' } },
      { name: 'millstone', type: 'interact', x: 8, y: 10, props: { dialogue: 'millstone' } },
      { name: 'ford_sign', type: 'interact', x: 12, y: 13, props: { dialogue: 'ford_sign' } },
      { name: 'flooded_ford', type: 'obstacle', x: 12, y: 15, w: 3, h: 2, props: { dialogue: 'ford_sign', opensIn: 'WW' } },
      { name: 'mill_window', type: 'light', x: 6, y: 9, props: { radius: 2.4, color: '#ffb35c', intensity: 0.7 } },
      { name: 'ford_lantern', type: 'light', x: 16, y: 11, props: { radius: 3.2, color: '#ffc46b', intensity: 0.95 } },
      { name: 'field_creep_a', type: 'creep_spawn', x: 21, y: 7, props: { template: 'furrow_mole', levelMin: 2, levelMax: 3, aggroRadius: 1 } },
      { name: 'field_creep_b', type: 'creep_spawn', x: 23, y: 9, props: { template: 'hedge_adder', levelMin: 2, levelMax: 3, aggroRadius: 2 } },
      { name: 'stream_nook', type: 'reserved', x: 4, y: 14, props: { for: 'chest', phase: 'V6' } },
    ],
  });
}

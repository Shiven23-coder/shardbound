// The Ash Rise (20 × 14): the rubble field north of the Old Ruins where the Ashen Herald waits (P-67).
//  - the way down to the ruins in the south
//  - a scorched floor ringed with fallen stones and dead trees; the Herald on the rise to the north
import {
  build, cluster, forestBorder, forestFill, makeTerrain, noise, paintEllipse, paintPath, pathEdges, smooth,
} from '../kit.mjs';

export default function ashRise() {
  const W = 20;
  const H = 14;
  const terrain = makeTerrain(W, H);
  forestBorder(terrain, 2, 2, 41);
  paintEllipse(terrain, 10, 6.5, 7.5, 5, 'grass');
  paintEllipse(terrain, 10, 5.5, 5, 3.2, 'dirt'); // the scorched floor
  paintPath(terrain, [[9.9, 15], [9.9, 9]], 1.0, 'dirt');
  smooth(terrain, 'dirt', 'grass');

  const forestTrees = forestFill(terrain, (x, y) => (noise(x, y, 42) < 0.8 ? 'deadTree' : 'pine'));

  const stamps = [
    ['rubble', 4, 4], ['rubble3', 15, 4], ['rubble2', 5, 8], ['rubble4', 14, 8],
    ['pillarBroken', 3, 6], ['pillarBroken', 16, 6], ['boulder', 6, 2], ['rock', 13, 2],
  ];

  const details = [];
  pathEdges(details, terrain, { verge: 'grim', edge: 'grit', density: 0.1, seed: 43 });
  cluster(details, 'debris', 10, 5, 3, 0.25, 44);

  return build({
    width: W,
    height: H,
    ground: 'dark',
    terrain,
    forestTrees,
    stamps,
    details,
    properties: { area: 'eldmere_ash_rise', region: 'eldmere' },
    objects: [
      { name: 'from_ruins', type: 'spawn_point', x: 10, y: 12, props: { main: true } },
      { name: 'to_ruins', type: 'exit', x: 9, y: 13, w: 2, h: 1, props: { toArea: 'eldmere_old_ruins', toSpawn: 'from_rise' } },
    ],
  });
}

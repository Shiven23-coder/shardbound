// Eldmere Square (34 × 24): the village around its old well.
//  - the plaza and the well
//  - Elder Maren's house, Maren at her door
//  - Corvin's stall under his lantern (after the Heartshard)
//  - Brann's forge with its kiln, anvil and weapon rack
//  - Tanner's Alley in the north-east: a narrow lane between Tanner's goods and his
//    house, ending at a locked iron gate; crates close it until the player
//    follows Corvin (an obstacle, P-45)
//  - a nook behind Maren's house: space for a chest (V5)
import {
  build, cluster, forestBorder, forestFill, makeTerrain, noise, paintEllipse, paintPath, pathEdges, smooth,
} from '../kit.mjs';

export default function square() {
  const W = 34;
  const H = 24;
  const terrain = makeTerrain(W, H);
  forestBorder(terrain, 2, 1, 31);
  // The plaza, the lane in from the west, the road south to the mill.
  paintEllipse(terrain, 17, 12, 7.5, 5, 'dirt');
  paintPath(terrain, [[-1, 12], [5, 12.4], [10, 12]], 1.25, 'dirt');
  paintPath(terrain, [[17, 15], [16.4, 19], [16.9, 25]], 1.1, 'dirt');
  // Tanner's Alley: a narrow way north to the iron gate.
  paintPath(terrain, [[24.5, 9.5], [27.5, 8.6], [27.5, 2.5]], 0.9, 'dirt');
  smooth(terrain, 'dirt', 'grass');
  paintPath(terrain, [[7.5, 6.5], [7.5, 8]], 0.5, 'dirt'); // Maren's door path
  paintEllipse(terrain, 3.5, 5, 1.8, 1.8, 'grass'); // the nook behind Maren's house

  const forestTrees = forestFill(terrain, (x, y) => {
    const n = noise(x, y, 32);
    return n < 0.6 ? 'pine' : n < 0.9 ? 'roundTree' : 'paleTree';
  });

  const stamps = [
    // the well
    ['oldWell', 17, 11],
    // Maren's house and garden
    ['houseRed', 6, 3], ['fenceLeft', 4, 7], ['fence', 5, 7], ['fenceRight', 6, 7], ['fenceLeft', 8, 7], ['fenceRight', 9, 7],
    ['shrub', 10, 5], ['barrel', 10, 7],
    // a neighbour's house
    ['houseBrown', 12, 2], ['crates', 16, 4], ['sacks', 11, 4],
    // Corvin's stall
    ['tent', 21, 6], ['basketFish', 20, 8], ['basketGreens', 21, 8], ['basketApples', 22, 8], ['lanternPost', 24, 5],
    // Tanner's Alley: his goods along the west side, his house and barrels along the east, the gate at its end
    ['houseTan', 29, 5], ['ironGate', 27, 1], ['ironFence', 26, 2], ['ironFence', 29, 2],
    ['barrelOpen', 26, 3], ['crates', 26, 4], ['sacks', 26, 5], ['barrel', 26, 6], ['crates', 26, 7],
    ['barrel', 29, 3], ['barrelOpen', 29, 4],
    // Brann's forge
    ['forge', 24, 14], ['kiln', 28, 14], ['anvil', 23, 17], ['weaponRack', 21, 14], ['barrel', 28, 17], ['crates', 29, 17],
    // houses on the south-west, a grove on the south-east
    ['houseWood', 4, 15], ['houseBrown', 9, 17], ['shrub2', 8, 15],
    ['roundTree', 25, 19], ['paleTree', 28, 19], ['shrub', 22, 20],
    // Tanner's yard, east of the plaza
    ['roundTree', 27, 10], ['shrub2', 30, 11], ['barrel', 29, 8], ['sacks', 31, 8],
  ];

  // Crates across the alley until Corvin leads the way (drawn only while they block).
  const obstacleStamps = [['crates', 27, 7], ['barrel', 28, 7]];

  const details = [];
  pathEdges(details, terrain, { verge: 'tufts', edge: 'grit', density: 0.06, seed: 33 });
  cluster(details, 'flowers', 4.5, 5.5, 1.2, 0.5, 34); // Maren's garden
  cluster(details, 'flowers', 10, 6, 1, 0.5, 35);
  cluster(details, 'stones', 26, 18, 1.5, 0.3, 36); // forge slag
  cluster(details, 'tufts', 29.5, 12.5, 1.8, 0.4, 37);
  cluster(details, 'flowers', 5, 20, 1.6, 0.4, 38);

  return build({
    width: W,
    height: H,
    terrain,
    forestTrees,
    stamps,
    obstacleStamps,
    details,
    properties: { area: 'eldmere_square', region: 'eldmere' },
    objects: [
      { name: 'from_lane', type: 'spawn_point', x: 1, y: 11, props: { main: true } },
      { name: 'from_mill', type: 'spawn_point', x: 16, y: 22 },
      { name: 'to_lane', type: 'exit', x: 0, y: 11, w: 1, h: 2, props: { toArea: 'eldmere_cottage_lane', toSpawn: 'from_square' } },
      { name: 'to_mill', type: 'exit', x: 16, y: 23, w: 2, h: 1, props: { toArea: 'eldmere_mill_road', toSpawn: 'from_square' } },
      { name: 'maren', type: 'npc', x: 7, y: 6, props: { npcId: 'maren', dialogue: 'maren' } },
      { name: 'corvin_stall', type: 'npc', x: 23, y: 7, props: { npcId: 'corvin', dialogue: 'corvin_offer', showIf: 'dawn', hideIf: 'following_corvin' } },
      { name: 'brann', type: 'npc', x: 24, y: 17, props: { npcId: 'brann', dialogue: 'brann' } },
      { name: 'square_dawn', type: 'trigger', x: 1, y: 10, w: 2, h: 4, props: { dialogue: 'square_dawn', showIf: 'dawn', once: true } },
      { name: 'alley_mouth', type: 'trigger', x: 27, y: 8, w: 2, h: 2, props: { dialogue: 'alley_mouth', showIf: 'following_corvin', once: true } },
      { name: 'alley_crates', type: 'obstacle', x: 27, y: 7, w: 2, h: 1, props: { dialogue: 'alley_crates', hideIf: 'following_corvin' } },
      { name: 'maren_window', type: 'light', x: 8, y: 4, props: { radius: 2.2, color: '#ffb35c', intensity: 0.75 } },
      { name: 'neighbour_window', type: 'light', x: 14, y: 3, props: { radius: 2.0, color: '#ffb35c', intensity: 0.6 } },
      { name: 'stall_lantern', type: 'light', x: 24, y: 5, props: { radius: 3.2, color: '#ffc46b', intensity: 0.95 } },
      { name: 'forge_glow', type: 'light', x: 28, y: 15, props: { radius: 3.4, color: '#ff7a3c', intensity: 1.0 } },
      { name: 'maren_nook', type: 'reserved', x: 3, y: 4, props: { for: 'chest', phase: 'V5' } },
    ],
  });
}

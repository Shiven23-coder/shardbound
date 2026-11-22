// Drafts Eldmere's four maps as Tiled JSON (.tmj) with the map kit
// (tools/maps/kit.mjs) and the rules in V2_SPEC §9. Each map is checked for
// reachability before it is written. Once a map is polished in Tiled, its
// .tmj becomes the source and its draft here is deleted.
//
//   node tools/maps/build-eldmere.mjs
import { writeMap } from './kit.mjs';
import cottageLane from './eldmere/cottage-lane.mjs';
import oldRuins from './eldmere/old-ruins.mjs';
import square from './eldmere/square.mjs';
import millRoad from './eldmere/mill-road.mjs';

writeMap('eldmere_cottage_lane', cottageLane());
writeMap('eldmere_old_ruins', oldRuins());
writeMap('eldmere_square', square());
writeMap('eldmere_mill_road', millRoad());

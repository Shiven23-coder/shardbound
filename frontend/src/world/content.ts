// Game data the client needs to draw and walk the world, imported from
// ../content at build time: the art manifest (P-34), the per-region lighting
// in grades.json, and the step timing from balance.json. Map and art files are
// hashed into the build as URLs (P-35). No Phaser here.
import manifest from '../../../content/art/manifest.json';
import grades from '../../../content/art/grades.json';
import balance from '../../../content/balance.json';

const mapUrls = import.meta.glob('../../../content/maps/*.tmj', {
  query: '?url',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const artUrls = import.meta.glob(
  ['../../../content/art/ninja-adventure/eldmere/**/*.png', '../../../content/art/shardbound/*.png'],
  { query: '?url', import: 'default', eager: true },
) as Record<string, string>;

const ART_PREFIX = '../../../content/art/';

/** The URL of a map file in the build, by map key (the file name without .tmj). */
export function mapUrl(mapKey: string): string {
  const url = mapUrls[`../../../content/maps/${mapKey}.tmj`];
  if (!url) throw new Error(`No map ${mapKey} in the build`);
  return url;
}

/** The URL of an art file in the build, by its path inside content/art. */
export function artUrl(pathInArt: string): string {
  const url = artUrls[ART_PREFIX + pathInArt];
  if (!url) throw new Error(`No art ${pathInArt} in the build`);
  return url;
}

/** Turns a map's tileset image path ("../art/...") into a build URL. */
export function tilesetUrl(imageInMap: string): string {
  return artUrl(imageInMap.replace(/^\.\.\/art\//, ''));
}

export interface ActorArt {
  sheet: string;
  faceset: string;
}

/** The art for a sprite key ("player", "npc.maren"). */
export function actorArt(key: string): ActorArt | undefined {
  return (manifest.actors as Record<string, ActorArt>)[key];
}

export const actorKeys = Object.keys(manifest.actors);
export const frameSize = manifest.frameSize;
export const sheetLayout = manifest.sheetLayout;
export const actorShadow = manifest.actorShadow;

export interface LightState {
  ambient: number;
  tint: number[];
  rain: boolean;
  playerLight: boolean;
}

export interface RegionLighting {
  states: Record<string, LightState>;
  lightRadiusScale: number;
  playerLight: { radius: number; color: string; intensity: number };
  rain: { opacity: number; dropsPerScreen: number; length: number[]; angle: number[]; splashes: boolean };
}

const DAYLIGHT: LightState = { ambient: 1, tint: [1, 1, 1], rain: false, playerLight: false };

/** A region's lighting, and the state for a light-state id (daylight if unknown). */
export function lightingFor(regionId: string, stateId: string): { region?: RegionLighting; state: LightState } {
  const region = (grades.lighting as Record<string, RegionLighting>)[regionId];
  return { region, state: region?.states[stateId] ?? DAYLIGHT };
}

/** One tile per stepMs (P-51); the server's speed check uses the same number. */
export const stepTiming = { stepMs: balance.world.stepMs, turnMs: balance.world.turnMs };
export const interactRange = balance.world.interactRange;

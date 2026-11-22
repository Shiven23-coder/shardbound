// Game data the client needs to draw and walk the world, imported from
// ../content at build time: the art manifest (P-34), the per-region lighting
// in grades.json, and the step timing from balance.json. Map files and the
// dialogue portraits are hashed into the build as URLs (P-35); every other art
// file is reached through ./art, which only the Phaser chunk loads, so battle
// art never weighs on the first page (P-57). No Phaser here.
import manifest from '../../../content/art/manifest.json';
import grades from '../../../content/art/grades.json';
import balance from '../../../content/balance.json';

const mapUrls = import.meta.glob('../../../content/maps/*.tmj', {
  query: '?url',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const facesetUrls = import.meta.glob('../../../content/art/ninja-adventure/eldmere/**/Faceset.png', {
  query: '?url',
  import: 'default',
  eager: true,
}) as Record<string, string>;

export const ART_PREFIX = '../../../content/art/';

/** The URL of a map file in the build, by map key (the file name without .tmj). */
export function mapUrl(mapKey: string): string {
  const url = mapUrls[`../../../content/maps/${mapKey}.tmj`];
  if (!url) throw new Error(`No map ${mapKey} in the build`);
  return url;
}

/** The URL of a portrait (an actor's faceset) in the build, by its path inside content/art. */
export function facesetUrl(pathInArt: string): string {
  const url = facesetUrls[ART_PREFIX + pathInArt];
  if (!url) throw new Error(`No portrait ${pathInArt} in the build`);
  return url;
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

/** An effect strip: one row of frames (P-57). */
export interface EffectArt {
  sheet: string;
  frameWidth: number;
  frameHeight: number;
  frames: number;
  frameRate: number;
}

export const effectArt = manifest.effects as Record<string, EffectArt>;
/** Which effect plays for a player's move by element, a creep's move, healing and escaping. */
export const battleEffects = manifest.battleEffects;
/** The battle backdrop strip: a treeline above tileable grass (P-57). */
export const battleBackdrop = manifest.battleBackdrop;

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

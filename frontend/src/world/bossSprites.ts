// The bosses' sprite strips (P-67). They load only where a boss can appear:
// with an area whose map names one (its boss object or its trigger), or with a
// boss battle, and they are let go again in an area without one. Kept loaded
// everywhere, they slowed every creep battle's first frame (the battle start
// budget, V2_SPEC §4.13). Each animation is its own strip.
import Phaser from 'phaser';
import { BOSS_ANIMATIONS, bossArt, bossKeys, bossSpriteKey, bossTexture } from './content';
import { artUrl } from './art';

/** A Tiled object as the map JSON has it: its type and its custom properties. */
interface TiledObject {
  type?: string;
  properties?: { name: string; value: unknown }[];
}

/** A Tiled map's layers, as far as its objects go. */
export interface TiledLayers {
  layers?: { objects?: TiledObject[] }[];
}

/** The boss sprite keys a Tiled map names: its boss objects' bossId and its triggers' boss. */
export function bossKeysOnMap(map: TiledLayers): string[] {
  const ids = new Set<string>();
  for (const layer of map.layers ?? []) {
    for (const o of layer.objects ?? []) {
      const property = (name: string) => o.properties?.find((p) => p.name === name)?.value;
      const id = o.type === 'boss' ? property('bossId') : o.type === 'trigger' ? property('boss') : undefined;
      if (typeof id === 'string') ids.add(id);
    }
  }
  return [...ids].map(bossSpriteKey).filter((key) => bossKeys.includes(key));
}

/** Queues the strips of these bosses that are not loaded yet. */
export function preloadBosses(scene: Phaser.Scene, keys: string[]): void {
  for (const key of keys) {
    const art = bossArt(key)!;
    for (const animation of BOSS_ANIMATIONS) {
      const texture = bossTexture(key, animation);
      if (!scene.textures.exists(texture)) {
        scene.load.spritesheet(texture, artUrl(art[animation].sheet), { frameWidth: art.frameSize, frameHeight: art.frameSize });
      }
    }
  }
}

/** Lets go of every boss's strips and animations but these. */
export function releaseBosses(scene: Phaser.Scene, keep: string[]): void {
  for (const key of bossKeys.filter((k) => !keep.includes(k))) {
    for (const animation of BOSS_ANIMATIONS) {
      scene.anims.remove(`${key}:${animation}`);
      const texture = bossTexture(key, animation);
      if (scene.textures.exists(texture)) scene.textures.remove(texture);
    }
  }
}

/** `<key>:idle` and `<key>:walk` loop; `<key>:attack`, `<key>:hit` and `<key>:transform` play once. */
export function createBossAnimations(scene: Phaser.Scene): void {
  for (const key of bossKeys) {
    const art = bossArt(key)!;
    for (const animation of BOSS_ANIMATIONS) {
      const name = `${key}:${animation}`;
      const texture = bossTexture(key, animation);
      if (scene.anims.exists(name) || !scene.textures.exists(texture)) continue;
      scene.anims.create({
        key: name,
        frames: scene.anims.generateFrameNumbers(texture, { start: 0, end: art[animation].frames - 1 }),
        frameRate: art[animation].frameRate,
        repeat: animation === 'idle' || animation === 'walk' ? -1 : 0,
      });
    }
  }
}

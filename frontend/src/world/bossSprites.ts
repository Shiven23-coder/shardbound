// The bosses' sprite strips (P-67): loaded with the world, so a boss is drawn at
// once on the map, in a cutscene and in battle. Each animation is its own strip;
// the animations are made once for the whole game.
import Phaser from 'phaser';
import { BOSS_ANIMATIONS, bossArt, bossKeys, bossTexture } from './content';
import { artUrl } from './art';

/** Queues every boss strip not loaded yet. */
export function preloadBosses(scene: Phaser.Scene): void {
  for (const key of bossKeys) {
    const art = bossArt(key)!;
    for (const animation of BOSS_ANIMATIONS) {
      const texture = bossTexture(key, animation);
      if (!scene.textures.exists(texture)) {
        scene.load.spritesheet(texture, artUrl(art[animation].sheet), { frameWidth: art.frameSize, frameHeight: art.frameSize });
      }
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

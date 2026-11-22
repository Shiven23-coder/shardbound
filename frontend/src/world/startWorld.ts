// Boots Phaser in `parent` with the world scene. This module is loaded with
// import() after the title screen, so Phaser stays out of the initial bundle
// (§4.13). Returns a function that tears the game down.
import Phaser from 'phaser';
import { WorldScene } from './WorldScene';
import type { WorldSceneData } from './WorldScene';
import type { WorldBridge } from './bridge';

export function startWorld(parent: HTMLElement, bridge: WorldBridge): () => void {
  if (!bridge.latestView) throw new Error('The world needs a view before it starts');
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    pixelArt: true,
    roundPixels: true,
    backgroundColor: '#07060a',
    banner: false,
    scale: { mode: Phaser.Scale.RESIZE, width: parent.clientWidth, height: parent.clientHeight },
    // The scene reads the keyboard from DOM events itself (keyboardInput.ts, P-51).
    input: { activePointers: 3, gamepad: true, keyboard: false },
  });
  const data: WorldSceneData = { bridge, view: bridge.latestView };
  game.scene.add('world', WorldScene, true, data);
  return () => game.destroy(true);
}

// The battle arena (P-57): the backdrop strip (a treeline over meadow grass,
// repeated across the screen and tinted by the light state), the enemy on its platform top-right
// and the player's back view bottom-left, drawn at a whole-number scale. A
// round's hits play in step with the battle text, each when the line that
// announces it shows: the attacker lunges, the move's effect strip plays on
// the target, the target flashes, a number floats up, and a critical hit
// shakes the camera. Skipping the text plays what is left at once, quickly. A
// won battle fades the enemy into smoke after the last blow's lines, a lost
// one the player. The info boxes, text and menus are React's. Reduce
// motion turns off the shake, flashes and lunges.
import Phaser from 'phaser';
import { audio } from '../audio/audio';
import type { Effect } from '../audio/audio';
import type { BattleView, HitView } from '../api/types';
import type { WorldBridge } from './bridge';
import { battleBackdrop, battleEffects, effectArt, sheetLayout } from './content';
import { reducedMotion } from './motion';

export const BATTLE_SCENE = 'battle';
/** The backdrop strip's texture key; the world scene loads it with the battle effects. */
export const BACKDROP_KEY = 'battle:backdrop';

const TILE = 16;
const SHEET_COLUMNS = 4;
/** Arena positions as shares of the visible area (above the text box). */
const HORIZON = 0.36;
const ENEMY_AT = { x: 0.7, y: 0.42 };
const PLAYER_AT = { x: 0.28, y: 0.86 };
/** Time between hits that are due together (the text was skipped). */
const CATCH_UP_GAP_MS = 240;

interface BattleSceneData {
  bridge: WorldBridge;
}

/** The backdrop by light state: the sky's gradient and a tint over trees and grass (the storm night is darker and cold). */
const LIGHTS: Record<string, { sky: [number, number]; tint: number }> = {
  storm_night: { sky: [0x0b1018, 0x1c2633], tint: 0x6a7890 },
  dawn: { sky: [0x3a3550, 0x8a6f72], tint: 0xf0d8c8 },
};

export class BattleScene extends Phaser.Scene {
  private bridge!: WorldBridge;
  private backdrop!: Phaser.GameObjects.Graphics;
  private treeline!: Phaser.GameObjects.TileSprite;
  private ground!: Phaser.GameObjects.TileSprite;
  private platforms!: Phaser.GameObjects.Graphics;
  private enemy!: Phaser.GameObjects.Sprite;
  private player!: Phaser.GameObjects.Sprite;
  private shadows: Phaser.GameObjects.Ellipse[] = [];
  private rain: Phaser.GameObjects.Particles.ParticleEmitter | null = null;
  private battle: BattleView | null = null;
  private shownRound = -1;
  /** How many of the round's hits have played. */
  private played = 0;
  private outcomePlayed = false;
  private scale$ = 4;
  private readonly unsubscribe: (() => void)[] = [];

  constructor() {
    super(BATTLE_SCENE);
  }

  init(data: BattleSceneData): void {
    this.bridge = data.bridge;
    this.battle = null;
    this.shownRound = -1;
    this.played = 0;
    this.outcomePlayed = false;
    this.shadows = [];
    this.rain = null;
  }

  create(): void {
    createEffectAnimations(this);
    cutBackdrop(this);
    this.backdrop = this.add.graphics();
    this.ground = this.add.tileSprite(0, 0, 1, 1, BACKDROP_GROUND).setOrigin(0, 0);
    this.treeline = this.add.tileSprite(0, 0, 1, 1, BACKDROP_TREELINE).setOrigin(0, 1);
    this.platforms = this.add.graphics();
    const first = this.bridge.latestBattle!.battle;
    this.enemy = this.add.sprite(0, 0, first.enemy.sprite, frame('DOWN')).setOrigin(0.5, 0.9);
    this.player = this.add.sprite(0, 0, first.player.sprite, frame('UP')).setOrigin(0.5, 0.9);
    this.enemy.anims.play({ key: `${first.enemy.sprite}:walk:DOWN`, frameRate: 4 });
    this.layout();
    this.cameras.main.setBackgroundColor('#07060a');
    if (!reducedMotion()) this.cameras.main.flash(260, 255, 255, 255);
    audio.play('battleStart');
    this.show(first);
    this.unsubscribe.push(this.bridge.toScene.on('battle', (next) => {
      if (next) {
        this.layout();
        this.show(next.battle);
      }
    }));
    this.unsubscribe.push(this.bridge.toScene.on('battleLine', () => this.catchUp()));
    const onResize = () => this.layout();
    this.scale.on(Phaser.Scale.Events.RESIZE, onResize);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.scale.off(Phaser.Scale.Events.RESIZE, onResize);
      this.unsubscribe.splice(0).forEach((off) => off());
      this.tweens.killAll();
    });
  }

  /** The visible arena: the canvas above the text box (in portrait the box is in the deck). */
  private arena(): { width: number; height: number } {
    const { width, height } = this.scale.gameSize;
    const cover = this.bridge.latestBattle?.cover ?? 0;
    return { width, height: height * (1 - cover) };
  }

  private layout(): void {
    const { width, height } = this.arena();
    this.scale$ = Math.max(2, Math.floor(Math.min(width * 0.9, height) / (TILE * 4.5)));
    const lightState = this.bridge.latestView?.lightState ?? 'dawn';
    const light = LIGHTS[lightState] ?? LIGHTS.dawn!;
    const horizon = Math.round(height * HORIZON);
    const fullHeight = this.scale.gameSize.height;
    // The trees are a step smaller than the fighters: they stand further back.
    const treeScale = Math.max(1, this.scale$ - 1);
    this.backdrop.clear()
      .fillGradientStyle(light.sky[0], light.sky[0], light.sky[1], light.sky[1], 1)
      .fillRect(0, 0, width, horizon);
    this.treeline.setPosition(0, horizon).setSize(width, battleBackdrop.treelineHeight * treeScale)
      .setTileScale(treeScale).setTint(light.tint);
    this.ground.setPosition(0, horizon).setSize(width, fullHeight - horizon).setTileScale(this.scale$).setTint(light.tint);
    const enemyAt = { x: width * ENEMY_AT.x, y: height * ENEMY_AT.y };
    const playerAt = { x: width * PLAYER_AT.x, y: height * PLAYER_AT.y };
    const g = this.platforms.clear();
    this.shadows.forEach((s) => s.destroy());
    this.shadows = [enemyAt, playerAt].map((at, i) => {
      // A patch of trodden grass under each fighter, with a lighter worn rim.
      const w = TILE * this.scale$ * (i === 0 ? 2.6 : 3.2);
      g.fillStyle(0xd8e0a8, 0.16).fillEllipse(at.x, at.y + 2, w + 8, w * 0.32 + 6);
      g.fillStyle(0x10140c, 0.3).fillEllipse(at.x, at.y, w, w * 0.32);
      return this.add.ellipse(at.x, at.y - 1, TILE * this.scale$ * 0.9, TILE * this.scale$ * 0.22, 0x000000, 0.35);
    });
    this.enemy.setPosition(enemyAt.x, enemyAt.y).setScale(this.scale$).setDepth(2);
    this.player.setPosition(playerAt.x, playerAt.y).setScale(this.scale$ + 1).setDepth(3);
    if (lightState === 'storm_night' && !this.rain && this.textures.exists('fx:rain')) {
      this.rain = this.add.particles(0, 0, 'fx:rain', {
        x: { min: 0, max: width }, y: -10, lifespan: 700, speedY: 520, speedX: -60, rotate: 7,
        alpha: { start: 0.35, end: 0.2 }, scaleY: { min: 0.8, max: 1.4 }, frequency: 25, quantity: 2,
      }).setDepth(10);
    }
  }

  /** A new round: its hits wait for their lines; a heal (the bag is used first) shows at once. */
  private show(battle: BattleView): void {
    const previous = this.battle;
    this.battle = battle;
    if (battle.round === this.shownRound) return;
    this.shownRound = battle.round;
    this.played = 0;
    this.outcomePlayed = false;
    if (previous && battle.player.hp > previous.player.hp) this.playEffect(battleEffects.heal, this.player, 'heal');
    this.catchUp();
  }

  /**
   * Plays every hit whose line has shown, spacing out several due at once; the
   * end of the battle follows once the line after the last blow has shown.
   */
  private catchUp(): void {
    const battle = this.battle;
    const shown = this.bridge.latestBattleLine;
    if (!battle || shown.round !== battle.round) return;
    let due = 0;
    while (this.played < battle.hits.length && battle.hits[this.played]!.line <= shown.line) {
      const hit = battle.hits[this.played]!;
      this.time.delayedCall(due++ * CATCH_UP_GAP_MS, () => this.playHit(hit));
      this.played++;
    }
    const lastBlow = battle.hits.at(-1)?.line ?? -1;
    if (battle.outcome && !this.outcomePlayed && this.played === battle.hits.length && shown.line > lastBlow) {
      this.outcomePlayed = true;
      const outcome = battle.outcome;
      this.time.delayedCall(due * CATCH_UP_GAP_MS + 300, () => this.playOutcome(outcome));
    }
  }

  private playHit(hit: HitView): void {
    const target = hit.onPlayer ? this.player : this.enemy;
    const attacker = hit.onPlayer ? this.enemy : this.player;
    const motion = !reducedMotion();
    if (hit.element && motion) {
      const toward = Math.sign(target.x - attacker.x) * TILE;
      this.tweens.add({ targets: attacker, x: attacker.x + toward, duration: 90, yoyo: true, ease: 'Quad.easeOut' });
    }
    if (hit.element) {
      const effect = hit.onPlayer ? battleEffects.creepMove : battleEffects.playerMove[hit.element as keyof typeof battleEffects.playerMove];
      this.playEffect(effect, target, soundFor(hit));
    }
    if (hit.amount > 0) {
      if (motion) this.flash(target);
      this.floatNumber(target, hit);
      if (hit.crit && motion) this.cameras.main.shake(200, 0.012);
    }
  }

  private playEffect(key: string, target: Phaser.GameObjects.Sprite, sound: Effect): void {
    audio.play(sound);
    if (!this.textures.exists(key)) return;
    const fx = this.add.sprite(target.x, target.y - (TILE * target.scaleY) / 2, key).setScale(Math.max(1, this.scale$ - 2)).setDepth(5);
    fx.play(`${key}:play`);
    fx.once(Phaser.Animations.Events.ANIMATION_COMPLETE, () => fx.destroy());
  }

  private flash(target: Phaser.GameObjects.Sprite): void {
    const white = () => target.setTint(0xffffff).setTintMode(Phaser.TintModes.FILL);
    const normal = () => target.clearTint().setTintMode(Phaser.TintModes.MULTIPLY);
    white();
    this.time.delayedCall(70, normal);
    this.time.delayedCall(140, white);
    this.time.delayedCall(210, normal);
  }

  private floatNumber(target: Phaser.GameObjects.Sprite, hit: HitView): void {
    const label = `${hit.crit ? 'CRIT ' : ''}-${hit.amount}`;
    const colour = hit.affinity === 'W' ? '#ffb347' : hit.affinity === 'R' ? '#aab0b8' : hit.status && !hit.element ? '#b48ad6' : '#ffffff';
    const text = this.add.text(target.x, target.y - TILE * target.scaleY, label, {
      fontFamily: 'system-ui, sans-serif', fontSize: `${Math.max(14, this.scale$ * 5)}px`, fontStyle: 'bold',
      color: colour, stroke: '#1a120a', strokeThickness: 4,
    }).setOrigin(0.5).setDepth(8);
    // It rises at once and stays readable, fading only at the end.
    this.tweens.add({
      targets: text, y: text.y - TILE * 2, alpha: { value: 0, ease: 'Quint.easeIn' }, duration: 1200, ease: 'Cubic.easeOut',
      onComplete: () => text.destroy(),
    });
  }

  private playOutcome(outcome: string): void {
    const motion = !reducedMotion();
    if (outcome === 'WON') {
      this.playEffect(battleEffects.escape, this.enemy, 'victory');
      this.tweens.add({ targets: this.enemy, alpha: 0, y: this.enemy.y + (motion ? 6 : 0), duration: 500 });
    } else if (outcome === 'LOST') {
      audio.play('defeat');
      this.tweens.add({ targets: this.player, alpha: 0.2, duration: 700 });
    } else {
      this.playEffect(battleEffects.escape, this.player, 'slash');
      this.tweens.add({ targets: this.player, alpha: 0, x: this.player.x - (motion ? TILE * 4 : 0), duration: 450 });
    }
  }
}

const BACKDROP_TREELINE = 'battle:treeline';
const BACKDROP_GROUND = 'battle:ground';

/** Cuts the backdrop strip into its treeline and grass, each a texture of its own that a TileSprite repeats. */
function cutBackdrop(scene: Phaser.Scene): void {
  if (scene.textures.exists(BACKDROP_GROUND)) return;
  const source = scene.textures.get(BACKDROP_KEY).getSourceImage() as HTMLImageElement;
  const parts: [string, number, number][] = [
    [BACKDROP_TREELINE, 0, battleBackdrop.treelineHeight],
    [BACKDROP_GROUND, battleBackdrop.treelineHeight, battleBackdrop.groundHeight],
  ];
  for (const [key, top, height] of parts) {
    const canvas = scene.textures.createCanvas(key, source.width, height)!;
    canvas.context.drawImage(source, 0, top, source.width, height, 0, 0, source.width, height);
    canvas.refresh();
  }
}

function frame(facing: keyof typeof sheetLayout.columns): number {
  return sheetLayout.walkRows[0]! * SHEET_COLUMNS + sheetLayout.columns[facing];
}

function soundFor(hit: HitView): Effect {
  if (hit.crit) return 'crit';
  if (hit.onPlayer) return 'hit';
  if (hit.element === 'FIRE') return 'fire';
  if (hit.element === 'PHYSICAL') return 'slash';
  return 'magic';
}

function createEffectAnimations(scene: Phaser.Scene): void {
  for (const [key, fx] of Object.entries(effectArt)) {
    if (scene.anims.exists(`${key}:play`) || !scene.textures.exists(key)) continue;
    scene.anims.create({
      key: `${key}:play`,
      frames: scene.anims.generateFrameNumbers(key, { start: 0, end: fx.frames - 1 }),
      frameRate: fx.frameRate,
    });
  }
}

// The walkable world (V2_SPEC §4.11, §9). The player walks in tile steps
// (P-51, see stepper.ts) against the map's collision layer, the active
// obstacles and the NPCs; the scene only reports world events (exits,
// triggers, interactions) through the bridge and draws what the server says
// (NPCs present, hidden objects, light state).
// Lighting: the scene is multiplied by the ambient light of the story state,
// and warm lights are additive sprites on top (map rule 6). The scene also
// plays the footsteps and the rain (P-42).
import Phaser from 'phaser';
import { audio, audioTiming } from '../audio/audio';
import type { CreepView, Facing, LastPosition, NpcView, WorldView } from '../api/types';
import type { Direction, WorldBridge } from './bridge';
import type { InputDevice } from '../input/inputMode';
import { KeyboardInput } from './keyboardInput';
import { nextTile, Stepper, type StepWorld, type Tile } from './stepper';
import { zoomFor } from './zoom';
import {
  actorArt, actorKeys, actorShadow, battleBackdrop, bossArt, bossTexture, chestArt, effectArt, frameSize, interactRange, lightingFor,
  mapUrl, sheetLayout, stepTiming,
} from './content';
import { artUrl, tilesetUrl } from './art';
import { BACKDROP_KEY, BATTLE_SCENE, BattleScene, createEffectAnimations } from './BattleScene';
import { createBossAnimations, preloadBosses } from './bossSprites';
import type { CutsceneCast } from './bridge';
import type { CutsceneStepView } from '../api/types';
import { reducedMotion } from './motion';
import type { LightState, RegionLighting } from './content';

const TILE = 16;
/** A typical landscape view in tiles: rain density is set per such screen. */
const REFERENCE_SCREEN_TILES = { x: 20, y: 11 };
const DEPTH = {
  ground: 0, detail: 1, shadows: 2, decor: 3, obstacles: 4, actors: 10, steam: 48, above: 50, dark: 60, light: 61, rain: 62, marker: 70,
};
const DRAWN_LAYERS = ['ground', 'detail', 'shadows', 'decor', 'obstacles', 'above'] as const;
/** Object types the player can use: talk to, read, look at (P-45 obstacles included). */
const USABLE = new Set(['npc', 'interact', 'obstacle', 'lantern', 'chest']);
/** Frames of Shardbound's props sheet (tools/art/props.mjs): a Lantern's top, lit and cold; the Lost Shards. */
const PROPS_FRAME = { lanternLit: 0, lanternCold: 4, lostShards: 5 } as const;
/** A kindled Lantern's green core glow (P-61), in tiles; it does not fade with daylight. */
const LANTERN_CORE = { radius: 0.9, colour: '#8fe3b0', alpha: 0.6 } as const;
/** A boss drawn on the map and in cutscenes (P-67): its 82 px frames at about three tiles tall. */
const BOSS_WORLD_SCALE = 0.55;
/** A cutscene actor walks a tile in this long; a boss, more slowly (P-68). */
const CUTSCENE_STEP_MS = { actor: 300, boss: 420 } as const;
/** The chest sprite (P-65) and its frames. */
const CHEST_KEY = 'chest';
const CHEST_FRAME = { closed: 0, open: 1 } as const;
/** A closed chest's faint glint (P-65), in tiles: drawn with the lights, so it shows through the treeline. */
const CHEST_GLINT = { radius: 0.7, colour: '#ffe7a3', alpha: 0.45 } as const;
/** Frames per second of a creep's idle loop. */
const CREEP_IDLE_FPS = 3;
/** A creep this many tiles beyond its aggro radius is near: the battle music starts loading (P-57). */
const CREEP_NEAR_TILES = 3;
/** The interaction marker's label per device (P-46). */
const MARKER_LABEL: Record<InputDevice, string> = { keyboard: 'E', gamepad: 'A', touch: 'A' };
const STICK_DEAD_ZONE = 0.4;
const LIGHT_TEXTURE = 'fx:light';
const LIGHT_TEXTURE_SIZE = 64;

/** A map object in tiles, with its properties as a plain record. */
interface MapObject {
  name: string;
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  props: Record<string, unknown>;
}

interface TiledTileset {
  name: string;
  image: string;
}

interface Actor {
  sprite: Phaser.GameObjects.Sprite;
  shadow: Phaser.GameObjects.Image;
}

export interface WorldSceneData {
  bridge: WorldBridge;
  view: WorldView;
}

export class WorldScene extends Phaser.Scene {
  private bridge!: WorldBridge;
  private view!: WorldView;
  private mapWidth = 0;
  private mapHeight = 0;
  private objects: MapObject[] = [];
  private collisionLayer: Phaser.Tilemaps.TilemapLayer | null = null;
  private obstacleLayer: Phaser.Tilemaps.TilemapLayer | null = null;
  /** The badge over the nearest usable object in reach, and what it marks. */
  private marker: Phaser.GameObjects.Container | null = null;
  private markerKey = '';
  private waterTiles: { x: number; y: number }[] = [];
  private player!: Phaser.GameObjects.Sprite;
  private playerShadow!: Phaser.GameObjects.Image;
  private playerLight: Phaser.GameObjects.Image | null = null;
  /** Where the player stands and walks, in whole tiles (P-51). */
  private stepper!: Stepper;
  private readonly stepWorld: StepWorld = {
    blocked: (tile) => this.blocked(tile),
    arrived: (tile) => this.checkStandingOn(tile) || this.checkLostShards(tile) || this.checkAggro(tile),
    stepStarted: () => this.stepStarted(),
  };
  private npcs = new Map<string, Actor>();
  private creeps = new Map<string, Actor & { view: CreepView }>();
  /** Creeps the player fled from: they attack again only once the player has been far enough away (P-54). */
  private disarmed = new Set<string>();
  /** Creeps close enough that the battle music was asked for. */
  private nearCreeps = new Set<string>();
  /** The tiles NPCs stand on: the player cannot step onto them. */
  private npcTiles = new Set<string>();
  private lighting: Phaser.GameObjects.GameObject[] = [];
  private props: Phaser.GameObjects.Image[] = [];
  /** Each Lantern's top, drawn lit or cold (P-61). */
  private lanternTops = new Map<string, Phaser.GameObjects.Image>();
  private chests = new Map<string, Phaser.GameObjects.Sprite>();
  private bosses = new Map<string, Phaser.GameObjects.Sprite>();
  /** The cutscene's actors by id while one plays (P-68). */
  private cutsceneActors = new Map<string, Phaser.GameObjects.Sprite>();
  /** The light state a cutscene shows instead of the view's (it keeps the old light until its LIGHT step). */
  private lightOverride: string | null = null;
  /** The light state last built. */
  private shownLight = '';
  /** The Lost Shards marker in this area (P-62). */
  private lostMarker: Phaser.GameObjects.Image | null = null;
  private keyboard!: KeyboardInput;
  /** The exit or trigger the player stands on: it fires once per entry. */
  private standingOn: string | null = null;
  /** The NPC, object or trigger last used: the camera frames it while its dialogue is open. */
  private focus: MapObject | null = null;
  private padAWasDown = false;
  /** The gamepad direction on the last frame, so a new one counts as a press. */
  private padDirection: Direction | null = null;
  /** What was last reported as in reach, so the report goes out only on a change. */
  private reachReported: 'npc' | 'lantern' | 'object' | null = null;
  private reachTile = '';
  private hasMoved = false;
  private hudCovered = false;
  /** E, Space, Enter or the gamepad's A went down: used once the step under way ends. */
  private interactQueued = false;
  /** Game time of the next footstep sound. */
  private nextStepAt = 0;
  private publishedPosition = '';
  /** A world event is on its way to the server. */
  private pending = false;
  private frozen = true;
  private readonly unsubscribe: (() => void)[] = [];

  constructor() {
    super('world');
  }

  init(data: WorldSceneData): void {
    this.bridge = data.bridge;
    this.view = data.view;
    this.frozen = data.bridge.frozen;
    this.pending = false;
    this.standingOn = null;
    this.focus = null;
    this.publishedPosition = '';
    this.reachReported = null;
    this.reachTile = '';
    this.marker = null;
    this.markerKey = '';
    this.hasMoved = false;
    this.hudCovered = false;
    this.interactQueued = false;
    this.padDirection = null;
    this.npcs = new Map();
    this.lanternTops = new Map();
    this.chests = new Map();
    this.bosses = new Map();
    this.cutsceneActors = new Map();
    this.lightOverride = null;
    this.shownLight = '';
    this.lostMarker = null;
    this.creeps = new Map();
    this.disarmed = new Set();
    this.nearCreeps = new Set();
    this.npcTiles = new Set();
    this.lighting = [];
    this.props = [];
    this.playerLight = null;
  }

  preload(): void {
    const mapKey = this.mapCacheKey();
    if (!this.cache.json.exists(mapKey)) {
      this.load.json(mapKey, mapUrl(this.view.mapKey));
      this.load.once(`filecomplete-json-${mapKey}`, (_key: string, _type: string, data: { tilesets: TiledTileset[] }) => {
        for (const tileset of data.tilesets) {
          if (!this.textures.exists(tilesetKey(tileset.name))) {
            this.load.image(tilesetKey(tileset.name), tilesetUrl(tileset.image));
          }
        }
      });
    }
    for (const key of actorKeys) {
      if (!this.textures.exists(key)) {
        this.load.spritesheet(key, artUrl(actorArt(key)!.sheet), { frameWidth: frameSize, frameHeight: frameSize });
      }
    }
    if (!this.textures.exists('actor-shadow')) this.load.image('actor-shadow', artUrl(actorShadow));
    preloadBosses(this);
    if (!this.textures.exists(CHEST_KEY)) {
      this.load.spritesheet(CHEST_KEY, artUrl(chestArt.sheet), { frameWidth: chestArt.frameWidth, frameHeight: chestArt.frameHeight });
    }
    // Battle effects load with the world, so a battle starts at once (V2_SPEC §4.13: < 300 ms).
    for (const [key, fx] of Object.entries(effectArt)) {
      if (!this.textures.exists(key)) {
        this.load.spritesheet(key, artUrl(fx.sheet), { frameWidth: fx.frameWidth, frameHeight: fx.frameHeight });
      }
    }
    if (!this.textures.exists(BACKDROP_KEY)) this.load.image(BACKDROP_KEY, artUrl(battleBackdrop.sheet));
  }

  create(): void {
    createEffectTextures(this);
    createWalkAnimations(this);
    createEffectAnimations(this);
    createBossAnimations(this);
    this.buildMap();
    this.createPlayer();
    this.showNpcs(this.view.npcs);
    this.showLanterns();
    this.showChests([]);
    this.showBosses();
    this.showLostShards();
    this.showCreeps(this.view.creeps);
    this.showProps();
    this.showObstacles();
    this.buildLighting();
    this.buildSteam();
    this.setUpCamera();
    this.setUpInput();

    this.unsubscribe.push(
      this.bridge.toScene.on('view', (view) => this.applyView(view)),
      this.bridge.toScene.on('snapBack', (position) => this.snapBack(position)),
      this.bridge.toScene.on('frozen', (frozen) => {
        this.frozen = frozen;
        if (!frozen) this.pending = false;
      }),
      this.bridge.toScene.on('interact', () => this.tryInteract()),
      this.bridge.toScene.on('press', (direction) => this.press(direction)),
      this.bridge.toScene.on('dialogue', (cover) => this.frameDialogue(cover)),
      this.bridge.toScene.on('battle', (battle) => this.showBattle(battle !== null)),
      this.bridge.toScene.on('cutscene', (cast) => (cast ? this.startCutscene(cast) : this.endCutscene())),
      this.bridge.toScene.on('cutsceneStep', (step) => this.stageStep(step)),
    );
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.unsubscribe.splice(0).forEach((off) => off());
      audio.setLoop('rain', false);
      // No position is published until the next area's scene has one.
      delete this.game.canvas.dataset.area;
    });
    // React may have moved on while the assets loaded: catch up with the bridge.
    this.frozen = this.bridge.frozen;
    if (this.bridge.latestView && this.bridge.latestView !== this.view) this.applyView(this.bridge.latestView);
    if (this.bridge.dialogueCover !== null) this.frameDialogue(this.bridge.dialogueCover);
    if (this.bridge.latestBattle) this.showBattle(true);
    if (this.bridge.latestCutscene) this.startCutscene(this.bridge.latestCutscene);
  }

  /** A battle takes the whole canvas in its own scene; the world sleeps until it is over (P-57). */
  private showBattle(on: boolean): void {
    const running = this.scene.isActive(BATTLE_SCENE);
    if (on && !running) {
      if (!this.scene.get(BATTLE_SCENE)) this.scene.add(BATTLE_SCENE, BattleScene, false);
      this.scene.launch(BATTLE_SCENE, { bridge: this.bridge });
      this.scene.sleep();
    } else if (!on && running) {
      this.scene.stop(BATTLE_SCENE);
      this.scene.wake();
    }
  }

  override update(): void {
    if (this.gamepadInteractPressed()) this.interactQueued = true;
    const padDirection = this.gamepadDirection();
    if (padDirection && padDirection !== this.padDirection) this.press(padDirection);
    this.padDirection = padDirection;
    const canWalk = this.canWalk();
    if (!canWalk) {
      // Nothing pressed while walking is paused carries over: a press that confirmed a dialogue never walks or talks.
      this.stepper.halt();
      this.interactQueued = false;
    }
    // The real frame time, not Phaser's smoothed one, so walking keeps time on a slow device (P-51).
    this.stepper.update(this.game.loop.rawDelta, canWalk ? this.heldDirection() : null, this.stepWorld);
    this.drawPlayer();
    this.publishPosition();
    this.checkHudCover();
    this.updateMarker();
    if (!this.canWalk() || this.stepper.walking) return;
    if (this.checkStandingOn(this.stepper.tile) || this.checkAggro(this.stepper.tile)) return;
    this.reportReach();
    if (this.interactQueued) {
      this.interactQueued = false;
      this.tryInteract();
    }
  }

  private canWalk(): boolean {
    return !this.frozen && !this.pending;
  }

  // ---------------------------------------------------------------------
  // map

  private mapCacheKey(): string {
    return `map:${this.view.mapKey}`;
  }

  private buildMap(): void {
    const data = this.cache.json.get(this.mapCacheKey());
    const tilemapKey = `tilemap:${this.view.mapKey}`;
    if (!this.cache.tilemap.exists(tilemapKey)) {
      this.cache.tilemap.add(tilemapKey, { format: Phaser.Tilemaps.Formats.TILED_JSON, data });
    }
    const map = this.make.tilemap({ key: tilemapKey });
    const tilesets = (data.tilesets as TiledTileset[])
      .map((t) => map.addTilesetImage(t.name, tilesetKey(t.name), TILE, TILE, 0, 0))
      .filter((t): t is Phaser.Tilemaps.Tileset => t !== null);
    for (const name of DRAWN_LAYERS) {
      if (name === 'obstacles') {
        // A CPU layer: its tiles show and hide one by one with their obstacle.
        const layer = map.createLayer(name, tilesets, 0, 0, false) as Phaser.Tilemaps.TilemapLayer | null;
        this.obstacleLayer = layer?.setDepth(DEPTH.obstacles) ?? null;
      } else {
        map.createLayer(name, tilesets)?.setDepth(DEPTH[name]);
      }
    }
    const collision = map.createLayer('collision', tilesets, 0, 0, false) as Phaser.Tilemaps.TilemapLayer | null;
    this.collisionLayer = collision?.setVisible(false) ?? null;
    this.mapWidth = map.widthInPixels;
    this.mapHeight = map.heightInPixels;
    this.objects = (map.getObjectLayer('objects')?.objects ?? []).map(toMapObject);
    const water = map.getTileset('water');
    this.waterTiles = [];
    if (water) {
      map.getLayer('ground')?.data.forEach((row) => row.forEach((tile) => {
        if (tile.index >= water.firstgid && tile.index < water.firstgid + water.total) {
          this.waterTiles.push({ x: tile.x, y: tile.y });
        }
      }));
    }
  }

  // ---------------------------------------------------------------------
  // player and NPCs

  private createPlayer(): void {
    const { x, y, facing } = this.view.player;
    this.stepper = new Stepper(tileAt(x, y), facing, stepTiming);
    const at = centreOf(this.stepper.tile);
    this.playerShadow = this.add.image(at.x, at.y + 1, 'actor-shadow').setDepth(DEPTH.shadows + 0.5);
    this.player = this.add.sprite(at.x, at.y, 'player', idleFrame(facing)).setOrigin(0.5, 0.75);
    this.drawPlayer();
  }

  private showNpcs(npcs: NpcView[]): void {
    const present = new Set(npcs.map((n) => n.id));
    for (const [id, actor] of this.npcs) {
      if (!present.has(id)) {
        actor.sprite.destroy();
        actor.shadow.destroy();
        this.npcs.delete(id);
      }
    }
    this.npcTiles = new Set([...npcs, ...this.view.creeps].map((actor) => tileKey(tileAt(actor.x, actor.y))));
    for (const npc of npcs) {
      if (this.npcs.has(npc.id)) continue;
      const key = `npc.${npc.npcId}`;
      const sprite = this.add.sprite(npc.x, npc.y, key, idleFrame('DOWN')).setOrigin(0.5, 0.75)
        .setDepth(DEPTH.actors + npc.y / 10000);
      const shadow = this.add.image(npc.x, npc.y + 1, 'actor-shadow').setDepth(DEPTH.shadows + 0.5);
      this.npcs.set(npc.id, { sprite, shadow });
    }
  }

  /**
   * Creeps standing on their spawn (V2_SPEC §4.2), each with a slow idle loop.
   * A creep the server marks wary (fled from) is disarmed until the player has
   * been far enough away (P-54).
   */
  private showCreeps(creeps: CreepView[]): void {
    const present = new Set(creeps.map((c) => c.id));
    for (const [id, actor] of this.creeps) {
      if (!present.has(id)) {
        actor.sprite.destroy();
        actor.shadow.destroy();
        this.creeps.delete(id);
        this.disarmed.delete(id);
      }
    }
    for (const creep of creeps) {
      if (!creep.wary) this.disarmed.delete(creep.id);
      else if (!this.creeps.get(creep.id)?.view.wary) this.disarmed.add(creep.id);
      const existing = this.creeps.get(creep.id);
      if (existing) {
        existing.view = creep;
        continue;
      }
      const sprite = this.add.sprite(creep.x, creep.y, creep.sprite, idleFrame('DOWN')).setOrigin(0.5, 0.75)
        .setDepth(DEPTH.actors + creep.y / 10000);
      sprite.anims.play({ key: `${creep.sprite}:walk:DOWN`, frameRate: CREEP_IDLE_FPS });
      const shadow = this.add.image(creep.x, creep.y + 1, 'actor-shadow').setDepth(DEPTH.shadows + 0.5);
      this.creeps.set(creep.id, { sprite, shadow, view: creep });
    }
    this.npcTiles = new Set([...this.view.npcs, ...creeps].map((actor) => tileKey(tileAt(actor.x, actor.y))));
  }

  /**
   * Coming within a creep's aggro radius starts a battle: the first such creep
   * by name, as the server picks (P-54). A disarmed creep re-arms once the
   * player is `aggroRadius + rearmTiles` away; one close by starts loading the
   * battle music. Returns true when an encounter was sent (the walk stops).
   */
  private checkAggro(tile: Tile): boolean {
    let engaged: CreepView | null = null;
    for (const { view: creep } of [...this.creeps.values()].sort((a, b) => a.view.id.localeCompare(b.view.id))) {
      const distance = chebyshev(tile, tileAt(creep.x, creep.y));
      if (this.disarmed.has(creep.id)) {
        if (distance >= creep.aggroRadius + creep.rearmTiles) this.disarmed.delete(creep.id);
        continue;
      }
      if (distance <= creep.aggroRadius + CREEP_NEAR_TILES && !this.nearCreeps.has(creep.id)) {
        this.nearCreeps.add(creep.id);
        this.bridge.toReact.emit('creepNear', undefined);
      }
      if (distance <= creep.aggroRadius && !engaged) engaged = creep;
    }
    if (!engaged || !this.canWalk()) return false;
    const { x, y } = this.position();
    this.pending = true;
    this.stepper.halt();
    this.bridge.toReact.emit('encounter', { creepId: engaged.id, x, y, facing: this.stepper.facing });
    return true;
  }

  // ---------------------------------------------------------------------
  // walking

  /** A key, D-pad or gamepad press: one queued step or turn, unless walking is paused. */
  private press(direction: Direction): void {
    if (this.canWalk()) this.stepper.press(direction);
  }

  private heldDirection(): Direction | null {
    return this.keyboard.held() ?? this.gamepadDirection() ?? this.bridge.direction;
  }

  /** The first gamepad's D-pad or left stick. */
  private gamepadDirection(): Direction | null {
    const pad = this.input.gamepad?.pad1;
    if (!pad) return null;
    const { x, y } = pad.leftStick;
    if (pad.left || x < -STICK_DEAD_ZONE) return 'LEFT';
    if (pad.right || x > STICK_DEAD_ZONE) return 'RIGHT';
    if (pad.up || y < -STICK_DEAD_ZONE) return 'UP';
    if (pad.down || y > STICK_DEAD_ZONE) return 'DOWN';
    return null;
  }

  /** True on the frame the first gamepad's A button goes down. */
  private gamepadInteractPressed(): boolean {
    const down = this.input.gamepad?.pad1?.A ?? false;
    const pressed = down && !this.padAWasDown;
    this.padAWasDown = down;
    return pressed;
  }

  /** A tile the player cannot step onto: outside the map, the collision layer, an active obstacle, a chest or an NPC. */
  private blocked(tile: Tile): boolean {
    if (tile.x < 0 || tile.y < 0 || tile.x * TILE >= this.mapWidth || tile.y * TILE >= this.mapHeight) return true;
    if (this.collisionLayer?.hasTileAt(tile.x, tile.y)) return true;
    if (this.npcTiles.has(tileKey(tile))) return true;
    if (this.view.chests.some((c) => tileKey(tileAt(c.x, c.y)) === tileKey(tile))) return true;
    if (this.view.bosses.some((b) => tileKey(tileAt(b.x, b.y)) === tileKey(tile))) return true;
    return this.objects.some((o) => o.type === 'obstacle' && this.isActive(o) && covers(o, tile));
  }

  private stepStarted(): void {
    if (!this.hasMoved) {
      this.hasMoved = true;
      this.bridge.toReact.emit('moved', undefined);
    }
    this.footstep();
  }

  /** One footstep per interval while walking, each a little higher or lower so they do not sound mechanical. */
  private footstep(): void {
    const now = this.time.now;
    if (now < this.nextStepAt) return;
    this.nextStepAt = now + audioTiming.footstepIntervalMs;
    audio.play('footstep', 1 + (Math.random() * 2 - 1) * audioTiming.footstepPitchJitter);
  }

  /** The player, its shadow and light where the stepper says, walking or standing; actors sort by their feet. */
  private drawPlayer(): void {
    const at = centreOf(this.stepper.drawn);
    const facing = this.stepper.facing;
    this.player.setPosition(at.x, at.y).setDepth(DEPTH.actors + at.y / 10000);
    if (this.stepper.walking) {
      this.player.anims.play(`player:walk:${facing}`, true);
    } else {
      this.player.anims.stop();
      this.player.setFrame(idleFrame(facing));
    }
    this.playerShadow.setPosition(at.x, at.y + 1);
    this.playerLight?.setPosition(at.x, at.y - 4);
  }

  /** The player's position as the server measures it: the centre of their tile, in pixels. */
  private position(): { x: number; y: number } {
    return centreOf(this.stepper.tile);
  }

  /**
   * Publishes the player's tile centre on the canvas element (data-x, data-y,
   * data-area), the facing and whether a step is under way (data-facing,
   * data-walking), where the player is drawn (data-dx, data-dy: the drawn
   * centre in map pixels) and on screen (data-sx, data-sy in CSS pixels,
   * data-zoom), whenever they change, so automation such as the e2e tests can
   * follow the player.
   */
  private publishPosition(): void {
    const { x, y } = this.position();
    const drawn = centreOf(this.stepper.drawn);
    const dx = Math.round(drawn.x);
    const dy = Math.round(drawn.y);
    const screen = this.screenPosition();
    const sx = Math.round(screen.x);
    const sy = Math.round(screen.y);
    const walking = this.stepper.walking ? '1' : '0';
    const value = `${this.view.areaId}:${x},${y}:${dx},${dy}:${sx},${sy}:${this.stepper.facing}:${walking}`;
    if (value === this.publishedPosition) return;
    this.publishedPosition = value;
    const { dataset } = this.game.canvas;
    dataset.area = this.view.areaId;
    dataset.x = String(x);
    dataset.y = String(y);
    dataset.dx = String(dx);
    dataset.dy = String(dy);
    dataset.facing = this.stepper.facing;
    dataset.walking = walking;
    dataset.sx = String(sx);
    dataset.sy = String(sy);
    dataset.zoom = String(this.cameras.main.zoom);
  }

  /** The player sprite's anchor (its feet) on screen, in CSS pixels from the game view's top-left corner. */
  private screenPosition(): { x: number; y: number } {
    const camera = this.cameras.main;
    return {
      x: (this.player.x - camera.worldView.x) * camera.zoom,
      y: (this.player.y - camera.worldView.y) * camera.zoom,
    };
  }

  /**
   * Tells React when the player (with a tile of room around them) is under the
   * HUD, so it can get out of the way (P-39). Checked every frame, sent on a change.
   */
  private checkHudCover(): void {
    const area = this.bridge.hudArea;
    const camera = this.cameras.main;
    const margin = TILE * camera.zoom;
    const { x: sx, y: sy } = this.screenPosition();
    const covered = area !== null && sx + margin > area.x && sx - margin < area.x + area.width
      && sy + margin > area.y && sy - 2 * margin < area.y + area.height;
    if (covered === this.hudCovered) return;
    this.hudCovered = covered;
    this.bridge.toReact.emit('hudCovered', covered);
  }

  // ---------------------------------------------------------------------
  // world events

  private isActive(o: MapObject): boolean {
    return !this.view.hiddenObjects.includes(o.name);
  }

  /** Something to use now: an active usable object, but not a chest that is already open. */
  private usable(o: MapObject): boolean {
    return USABLE.has(o.type) && this.isActive(o) && !this.view.chests.some((c) => c.id === o.name && c.open);
  }

  /**
   * Arriving on an exit or a trigger sends it, once per entry. Returns true when
   * it did, which stops the walk there.
   */
  private checkStandingOn(tile: Tile): boolean {
    const here = this.objects.find((o) => (o.type === 'exit' || o.type === 'trigger') && this.isActive(o) && covers(o, tile));
    if (!here) {
      this.standingOn = null;
      return false;
    }
    if (here.name === this.standingOn || !this.canWalk()) return false;
    this.standingOn = here.name;
    this.send(here);
    return true;
  }

  /** The NPC or object in front of the player, else the nearest one in reach; waits for the step under way. */
  tryInteract(): void {
    if (!this.canWalk()) return;
    if (this.stepper.walking) {
      this.interactQueued = true;
      return;
    }
    const target = this.target(this.stepper.tile);
    if (target) this.send(target);
  }

  private inReach(tile: Tile): MapObject[] {
    return this.objects.filter((o) => this.usable(o) && distance(o, tile) <= interactRange);
  }

  /** What E would use now: the object in front of the player, else the nearest in reach. */
  private target(tile: Tile): MapObject | null {
    const inReach = this.inReach(tile);
    if (inReach.length === 0) return null;
    const ahead = nextTile(tile, this.stepper.facing);
    return inReach.find((o) => covers(o, ahead)) ?? inReach.sort((a, b) => distance(a, tile) - distance(b, tile))[0]!;
  }

  /**
   * The interaction marker (P-46): a small badge with the key or button for the
   * device in use, over what E would use now. Hidden while walking is paused.
   */
  private updateMarker(): void {
    const target = this.canWalk() ? this.target(this.stepper.tile) : null;
    const device = this.bridge.device;
    const key = target ? `${target.name}:${device}` : '';
    if (key === this.markerKey) return;
    this.markerKey = key;
    this.marker?.destroy();
    this.marker = null;
    if (!target) return;
    const label = this.add.text(0, 0, MARKER_LABEL[device], {
      fontFamily: 'system-ui, sans-serif', fontSize: '7px', fontStyle: 'bold', color: '#1a120a',
    }).setOrigin(0.5).setResolution(Math.max(4, this.cameras.main.zoom * window.devicePixelRatio));
    const badge = this.add.graphics();
    badge.fillStyle(0xf3d9a4, 0.95).fillRoundedRect(-5, -5, 10, 10, 3).lineStyle(1, 0x1a120a, 0.8).strokeRoundedRect(-5, -5, 10, 10, 3);
    const x = (target.x + target.width / 2) * TILE;
    const y = target.y * TILE - (target.type === 'npc' ? 14 : 4);
    this.marker = this.add.container(x, y, [badge, label]).setDepth(DEPTH.marker);
    this.tweens.add({ targets: this.marker, y: y - 2, duration: 600, yoyo: true, repeat: -1, ease: 'Sine.easeInOut' });
  }

  /** Tells React when something usable comes into or leaves reach (for the interact hint), checked per tile. */
  private reportReach(): void {
    const tile = this.stepper.tile;
    const key = `${this.view.areaId}:${tile.x},${tile.y}`;
    if (key === this.reachTile) return;
    this.reachTile = key;
    const inReach = this.inReach(tile);
    const kind = inReach.length === 0 ? null : inReach.some((o) => o.type === 'npc') ? 'npc'
      : inReach.some((o) => o.type === 'lantern') ? 'lantern' : 'object';
    if (kind === this.reachReported) return;
    this.reachReported = kind;
    this.bridge.toReact.emit('reach', kind);
  }

  /** A tap on an NPC or object in reach uses it (between steps). */
  private interactAt(worldX: number, worldY: number): void {
    if (!this.canWalk() || this.stepper.walking) return;
    const tapped = { x: Math.floor(worldX / TILE), y: Math.floor(worldY / TILE) };
    const target = this.objects.find((o) => this.usable(o)
      && (covers(o, tapped) || covers(o, { x: tapped.x, y: tapped.y + 1 })));
    if (target && distance(target, this.stepper.tile) <= interactRange) this.send(target);
  }

  private send(target: MapObject): void {
    const { x, y } = this.position();
    const facing = this.stepper.facing;
    this.pending = true;
    this.stepper.halt();
    if (target.type !== 'exit') this.focus = target;
    if (target.type === 'npc') {
      // The NPC turns to the player it is talking to.
      this.npcs.get(target.name)?.sprite.setFrame(idleFrame(facingToward(target, this.stepper.tile)));
    }
    if (target.type === 'exit') {
      this.bridge.toReact.emit('exit', { exitId: target.name, x, y, facing });
    } else {
      this.bridge.toReact.emit('interact', { targetId: target.name, x, y, facing });
    }
  }

  private applyView(view: WorldView): void {
    const areaChanged = view.areaId !== this.view.areaId;
    const moved = view.player.x !== this.view.player.x || view.player.y !== this.view.player.y;
    const kindled = view.lanterns.filter((l) => l.lit && !this.view.lanterns.find((o) => o.id === l.id)?.lit);
    const opened = view.chests.filter((c) => c.open && this.view.chests.some((o) => o.id === c.id && !o.open)).map((c) => c.id);
    const lightChanged = view.lightState !== this.view.lightState
      || view.hiddenObjects.join() !== this.view.hiddenObjects.join() || kindled.length > 0 || opened.length > 0;
    this.view = view;
    this.pending = false;
    if (areaChanged) {
      this.scene.restart({ bridge: this.bridge, view });
      return;
    }
    this.showNpcs(view.npcs);
    this.showCreeps(view.creeps);
    this.showLanterns();
    this.showChests(opened);
    this.showBosses();
    this.showLostShards();
    if (kindled.length > 0) audio.play('lanternKindle');
    if (opened.length > 0) audio.play('chestOpen');
    this.showProps();
    this.showObstacles();
    if (lightChanged) this.buildLighting();
    // The server moved the player (the respawn after a lost battle, P-55).
    const serverTile = tileAt(view.player.x, view.player.y);
    if (moved && !this.stepper.walking && tileKey(serverTile) !== tileKey(this.stepper.tile)) {
      this.stepper.place(serverTile, view.player.facing);
    }
  }

  /**
   * Obstacles block and are drawn while active, and vanish when their flag
   * removes them (P-45). Their names are published on the canvas
   * (data-obstacles) for automation such as the e2e walker.
   */
  private showObstacles(): void {
    const active: string[] = [];
    for (const o of this.objects.filter((obj) => obj.type === 'obstacle')) {
      const on = this.isActive(o);
      this.obstacleLayer?.forEachTile((tile) => tile.setVisible(on), this, o.x, o.y, o.width, o.height);
      if (on) active.push(o.name);
    }
    this.game.canvas.dataset.obstacles = active.join(',');
    this.markerKey = '';
  }

  /**
   * Each Lantern's top above its post (P-61): its glass lit or cold. The light
   * of a lit Lantern is drawn with the area's other lights.
   */
  private showLanterns(): void {
    for (const lantern of this.view.lanterns) {
      const frame = this.tileFrame('props', lantern.lit ? PROPS_FRAME.lanternLit : PROPS_FRAME.lanternCold);
      const top = this.lanternTops.get(lantern.id)
        ?? this.add.image(lantern.x, lantern.y - TILE, tilesetKey('props'), frame).setDepth(DEPTH.above);
      top.setFrame(frame);
      this.lanternTops.set(lantern.id, top);
    }
  }

  /**
   * The chests present now (P-65), closed or open. Those in `opened` were opened just now and play their
   * opening: the lid pops and sparks fly (only the open frame under reduced motion). Their state is
   * published on the canvas (data-chests, "id:open" or "id:closed", comma-separated) for automation
   * such as the e2e tests.
   */
  private showChests(opened: string[]): void {
    for (const [id, sprite] of this.chests) {
      if (!this.view.chests.some((c) => c.id === id)) {
        sprite.destroy();
        this.chests.delete(id);
      }
    }
    for (const chest of this.view.chests) {
      const frame = chest.open ? CHEST_FRAME.open : CHEST_FRAME.closed;
      let sprite = this.chests.get(chest.id);
      if (!sprite) {
        sprite = this.add.sprite(chest.x, chest.y, CHEST_KEY, frame).setDepth(DEPTH.actors + (chest.y + TILE / 2) / 10000);
        this.chests.set(chest.id, sprite);
      }
      if (opened.includes(chest.id)) {
        this.playChestOpening(sprite);
      } else {
        sprite.setFrame(frame);
      }
    }
    this.game.canvas.dataset.chests = this.view.chests.map((c) => `${c.id}:${c.open ? 'open' : 'closed'}`).join(',');
    this.markerKey = '';
  }

  private playChestOpening(sprite: Phaser.GameObjects.Sprite): void {
    if (reducedMotion()) {
      sprite.setFrame(CHEST_FRAME.open);
      return;
    }
    this.tweens.add({
      targets: sprite, scaleY: 0.8, scaleX: 1.12, duration: 90, yoyo: true, ease: 'Quad.easeOut',
      onYoyo: () => sprite.setFrame(CHEST_FRAME.open),
    });
    if (this.anims.exists('fx.spark:play')) {
      // Above the darkness, so the sparks shine at night and at dawn.
      const sparks = this.add.sprite(sprite.x, sprite.y - TILE / 2, 'fx.spark').setScale(0.6).setDepth(DEPTH.marker);
      sparks.play('fx.spark:play');
      sparks.once(Phaser.Animations.Events.ANIMATION_COMPLETE, () => sparks.destroy());
    }
  }

  /** The bosses standing on the map now (P-67), each on its idle loop; a fallen boss is gone. */
  private showBosses(): void {
    for (const [id, sprite] of this.bosses) {
      if (!this.view.bosses.some((b) => b.id === id)) {
        sprite.destroy();
        this.bosses.delete(id);
      }
    }
    for (const boss of this.view.bosses) {
      if (this.bosses.has(boss.id)) continue;
      this.bosses.set(boss.id, this.bossSprite(boss.sprite, boss.x, boss.y));
    }
    this.game.canvas.dataset.bosses = this.view.bosses.map((b) => b.id).join(',');
  }

  private bossSprite(key: string, x: number, y: number): Phaser.GameObjects.Sprite {
    return this.add.sprite(x, y, bossTexture(key, 'idle'), 0).setOrigin(0.5, 0.8).setScale(BOSS_WORLD_SCALE)
      .setDepth(DEPTH.actors + y / 10000).play(`${key}:idle`);
  }

  // ---------------------------------------------------------------------
  // cutscenes (P-68): React plays the lines and the music; the scene stages the rest

  /** A cutscene begins: its cast takes the stage, the camera stops following, and the light holds. */
  private startCutscene(cast: CutsceneCast): void {
    this.endCutscene();
    this.lightOverride = this.shownLight || this.view.lightState;
    this.cameras.main.stopFollow();
    for (const actor of cast.actors) {
      const at = centreOf({ x: actor.x, y: actor.y });
      const sprite = bossArt(actor.sprite)
        ? this.bossSprite(actor.sprite, at.x, at.y)
        : this.add.sprite(at.x, at.y, actor.sprite, idleFrame(actor.facing as Facing)).setOrigin(0.5, 0.75)
          .setDepth(DEPTH.actors + at.y / 10000);
      this.cutsceneActors.set(actor.id, sprite);
    }
    this.game.canvas.dataset.cutscene = cast.id;
  }

  /** The cutscene ended (played or skipped): the cast leaves, the light and the camera follow the world again. */
  private endCutscene(): void {
    this.cutsceneActors.forEach((sprite) => {
      this.tweens.killTweensOf(sprite);
      sprite.destroy();
    });
    this.cutsceneActors.clear();
    delete this.game.canvas.dataset.cutscene;
    if (this.lightOverride !== null) {
      this.lightOverride = null;
      this.buildLighting();
    }
    this.cameras.main.resetFX();
    this.cameras.main.startFollow(this.player, true, 0.2, 0.2);
  }

  /** Stages one step, then tells React it is done. Reduce motion drops shakes, flashes and camera moves (P-68). */
  private stageStep(step: CutsceneStepView): void {
    const done = () => this.bridge.toReact.emit('cutsceneStepDone', undefined);
    const motion = !reducedMotion();
    const camera = this.cameras.main;
    const ms = step.ms ?? 0;
    switch (step.kind) {
      case 'MOVE':
        this.moveActor(step, done);
        return;
      case 'FACE': {
        const actor = step.actor ? this.cutsceneActors.get(step.actor) : undefined;
        if (actor && !bossArt(String(actor.texture.key).split('#')[0]!) && step.facing) {
          actor.setFrame(idleFrame(step.facing as Facing));
        }
        done();
        return;
      }
      case 'CAMERA':
        this.stageCamera(step, motion, done);
        return;
      case 'FADE':
        // A fade stays under reduce motion, as a short cross-fade.
        if (step.state === 'out') camera.fadeOut(motion ? ms : Math.min(ms, 200));
        else camera.fadeIn(motion ? ms : Math.min(ms, 200));
        this.time.delayedCall(motion ? ms : Math.min(ms, 200), done);
        return;
      case 'FLASH':
        if (motion) camera.flash(ms, 255, 240, 220);
        this.time.delayedCall(motion ? ms : 0, done);
        return;
      case 'WAIT':
        this.time.delayedCall(ms, done);
        return;
      case 'LIGHT':
        this.lightOverride = step.state ?? null;
        this.buildLighting();
        done();
        return;
      case 'VANISH':
        this.vanishActor(step, motion, done);
        return;
      default:
        done();                                 // lines, music and sounds are React's
    }
  }

  private moveActor(step: CutsceneStepView, done: () => void): void {
    const actor = step.actor ? this.cutsceneActors.get(step.actor) : undefined;
    if (!actor || step.path.length === 0) {
      done();
      return;
    }
    const key = String(actor.texture.key).split('#')[0]!;
    const boss = Boolean(bossArt(key));
    const stepMs = boss ? CUTSCENE_STEP_MS.boss : CUTSCENE_STEP_MS.actor;
    const walk = (from: { x: number; y: number }, to: { x: number; y: number }) => {
      if (boss) return `${key}:walk`;
      const dx = to.x - from.x;
      const dy = to.y - from.y;
      const facing: Facing = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 'LEFT' : 'RIGHT') : dy < 0 ? 'UP' : 'DOWN';
      return `${key}:walk:${facing}`;
    };
    const tiles = step.path.map((t) => centreOf(t));
    const next = (i: number) => {
      if (i >= tiles.length) {
        if (boss) actor.play(`${key}:idle`);
        else actor.anims.stop();
        done();
        return;
      }
      const to = tiles[i]!;
      actor.play(walk({ x: actor.x, y: actor.y }, to), true);
      this.tweens.add({
        targets: actor, x: to.x, y: to.y, duration: stepMs, ease: 'Linear',
        onUpdate: () => actor.setDepth(DEPTH.actors + actor.y / 10000),
        onComplete: () => next(i + 1),
      });
    };
    next(0);
  }

  private stageCamera(step: CutsceneStepView, motion: boolean, done: () => void): void {
    const camera = this.cameras.main;
    const ms = step.ms ?? 800;
    switch (step.camera) {
      case 'pan': {
        const actor = step.actor ? this.cutsceneActors.get(step.actor) : undefined;
        const at = actor ? { x: actor.x, y: actor.y } : centreOf({ x: step.x ?? 0, y: step.y ?? 0 });
        camera.stopFollow();
        if (motion) {
          camera.pan(at.x, at.y, ms, 'Sine.easeInOut', true);
          this.time.delayedCall(ms, done);
        } else {
          camera.centerOn(at.x, at.y);         // a cut, not a move
          done();
        }
        return;
      }
      case 'zoom':
        if (motion && step.zoom) {
          camera.zoomTo(step.zoom, ms);
          this.time.delayedCall(ms, done);
        } else {
          if (step.zoom) camera.setZoom(step.zoom);
          done();
        }
        return;
      case 'shake':
        if (motion) camera.shake(ms, 0.008);
        this.time.delayedCall(motion ? ms : 0, done);
        return;
      default:
        camera.startFollow(this.player, true, 0.2, 0.2);
        done();
    }
  }

  private vanishActor(step: CutsceneStepView, motion: boolean, done: () => void): void {
    const actor = step.actor ? this.cutsceneActors.get(step.actor) : undefined;
    if (!actor) {
      done();
      return;
    }
    const effect = step.effect ?? '';
    if (motion && this.anims.exists(`${effect}:play`)) {
      const smoke = this.add.sprite(actor.x, actor.y - TILE, effect).setDepth(DEPTH.above);
      smoke.play(`${effect}:play`);
      smoke.once(Phaser.Animations.Events.ANIMATION_COMPLETE, () => smoke.destroy());
    }
    this.tweens.add({
      targets: actor, alpha: 0, duration: motion ? step.ms ?? 600 : 0,
      onComplete: () => {
        actor.destroy();
        if (step.actor) this.cutsceneActors.delete(step.actor);
        done();
      },
    });
  }

  /** The Lost Shards (P-62): a pulsing glow on the tile where the player fell, until recovered. */
  private showLostShards(): void {
    const lost = this.view.lostShards;
    // Published on the canvas (data-lost-shards, a tile) for automation such as the e2e walker.
    this.game.canvas.dataset.lostShards = lost ? `${Math.floor(lost.x / TILE)},${Math.floor(lost.y / TILE)}` : '';
    if (!lost) {
      this.lostMarker?.destroy();
      this.lostMarker = null;
      return;
    }
    if (this.lostMarker) return;
    this.lostMarker = this.add.image(lost.x, lost.y, tilesetKey('props'), this.tileFrame('props', PROPS_FRAME.lostShards))
      .setDepth(DEPTH.actors + lost.y / 10000);
    this.lostMarker.setData('glow', this.addLight(lost.x, lost.y, TILE * 1.4, '#8fe3b0', 0.55));
    if (!reducedMotion()) {
      this.tweens.add({ targets: this.lostMarker, alpha: 0.55, duration: 900, yoyo: true, repeat: -1, ease: 'Sine.easeInOut' });
    }
    this.lostMarker.once(Phaser.GameObjects.Events.DESTROY, (marker: Phaser.GameObjects.Image) =>
      (marker.getData('glow') as Phaser.GameObjects.Image | undefined)?.destroy());
  }

  /**
   * Stepping within one tile of the Lost Shards picks them up (V2_SPEC §4.4,
   * P-62): the scene sends the recovery and stops. Returns true when it did.
   */
  private checkLostShards(tile: Tile): boolean {
    const lost = this.view.lostShards;
    if (!lost || !this.canWalk() || chebyshev(tile, tileAt(lost.x, lost.y)) > 1) return false;
    const { x, y } = this.position();
    this.pending = true;
    this.stepper.halt();
    this.bridge.toReact.emit('recover', { x, y, facing: this.stepper.facing });
    return true;
  }

  /** A frame for one 16 × 16 tile of a tileset texture, added on first use. */
  private tileFrame(tileset: string, id: number): string {
    const texture = this.textures.get(tilesetKey(tileset));
    const frame = `tile-${id}`;
    if (!texture.has(frame)) {
      const columns = texture.getSourceImage().width / TILE;
      texture.add(frame, 0, (id % columns) * TILE, Math.floor(id / columns) * TILE, TILE, TILE);
    }
    return frame;
  }

  /** Prop objects present now, drawn from a tile of their tileset ("props:2"). */
  private showProps(): void {
    this.props.splice(0).forEach((image) => image.destroy());
    for (const o of this.objects.filter((obj) => obj.type === 'prop' && this.isActive(obj))) {
      const [tileset, id] = String(o.props.sprite).split(':');
      const frame = this.tileFrame(tileset!, Number(id));
      this.props.push(this.add.image((o.x + 0.5) * TILE, (o.y + 0.5) * TILE, tilesetKey(tileset!), frame)
        .setDepth(DEPTH.actors + ((o.y + 1) * TILE) / 10000));
    }
  }

  private snapBack(position: LastPosition): void {
    this.pending = false;
    this.standingOn = null;
    if (position.areaId !== this.view.areaId) return;
    this.stepper.place(tileAt(position.x, position.y), position.facing);
    this.drawPlayer();
  }

  // ---------------------------------------------------------------------
  // light, rain, steam

  private buildLighting(): void {
    this.lighting.splice(0).forEach((o) => {
      this.tweens.killTweensOf(o);
      o.destroy();
    });
    this.playerLight = null;
    this.shownLight = this.lightOverride ?? this.view.lightState;
    const { region, state } = lightingFor(this.view.regionId, this.shownLight);
    const darkness = this.add.rectangle(0, 0, this.mapWidth, this.mapHeight, ambientColour(state))
      .setOrigin(0, 0).setDepth(DEPTH.dark).setBlendMode(Phaser.BlendModes.MULTIPLY);
    this.lighting.push(darkness);
    audio.setLoop('rain', Boolean(region && state.rain));
    if (!region) return;
    const strength = Phaser.Math.Clamp((1 - state.ambient) * 1.6, 0.15, 1);
    for (const o of this.objects.filter((obj) => obj.type === 'light' && this.isActive(obj))) {
      const radius = Number(o.props.radius) * TILE * region.lightRadiusScale;
      this.lighting.push(this.addLight((o.x + 0.5) * TILE, (o.y + 0.5) * TILE, radius, String(o.props.color),
        Number(o.props.intensity) * strength));
    }
    for (const lantern of this.view.lanterns.filter((l) => l.lit)) {
      const o = this.objects.find((obj) => obj.name === lantern.id);
      if (!o) continue;
      this.lighting.push(this.addLight(lantern.x, lantern.y - TILE / 2, Number(o.props.radius) * TILE * region.lightRadiusScale,
        String(o.props.color), Number(o.props.intensity) * strength));
      // The green heart of a kindled Lantern, seen by day too: what sets it apart from a street lamp.
      const core = this.addLight(lantern.x, lantern.y - TILE, TILE * LANTERN_CORE.radius, LANTERN_CORE.colour, LANTERN_CORE.alpha);
      this.lighting.push(core);
      if (!reducedMotion()) {
        this.tweens.add({ targets: core, alpha: LANTERN_CORE.alpha * 0.6, duration: 1400, yoyo: true, repeat: -1, ease: 'Sine.easeInOut' });
      }
    }
    for (const chest of this.view.chests.filter((c) => !c.open)) {
      const glint = this.addLight(chest.x, chest.y - TILE / 4, TILE * CHEST_GLINT.radius, CHEST_GLINT.colour, CHEST_GLINT.alpha);
      this.lighting.push(glint);
      if (!reducedMotion()) {
        this.tweens.add({
          targets: glint, alpha: 0.08, duration: 700, hold: 1600, repeatDelay: 1900, yoyo: true, repeat: -1, ease: 'Sine.easeInOut',
        });
      }
    }
    if (state.playerLight) {
      const { radius, color, intensity } = region.playerLight;
      this.playerLight = this.addLight(this.player.x, this.player.y, radius * TILE, color, intensity);
      this.lighting.push(this.playerLight);
    }
    if (state.rain) this.buildRain(region);
  }

  private addLight(x: number, y: number, radius: number, colour: string, alpha: number): Phaser.GameObjects.Image {
    return this.add.image(x, y, LIGHT_TEXTURE)
      .setScale((radius * 2) / LIGHT_TEXTURE_SIZE)
      .setTint(Phaser.Display.Color.HexStringToColor(colour).color)
      .setAlpha(alpha)
      .setBlendMode(Phaser.BlendModes.ADD)
      .setDepth(DEPTH.light);
  }

  /** Thin streaks in three slightly different angles and lengths; a few splashes on the water. */
  private buildRain(region: RegionLighting): void {
    const { opacity, dropsPerScreen, length, angle, splashes } = region.rain;
    const screenArea = REFERENCE_SCREEN_TILES.x * REFERENCE_SCREEN_TILES.y * TILE * TILE;
    const alive = Math.round((dropsPerScreen * this.mapWidth * this.mapHeight) / screenArea);
    const lifespan = 420;
    const angles = [angle[0]!, (angle[0]! + angle[1]!) / 2, angle[1]!];
    for (const degrees of angles) {
      const radians = Phaser.Math.DegToRad(degrees);
      const speed = 340;
      const perEmitter = alive / angles.length;
      const frequency = 40;
      this.lighting.push(this.add.particles(0, 0, 'fx:rain', {
        x: { min: -32, max: this.mapWidth + 32 },
        y: { min: -48, max: this.mapHeight },
        lifespan,
        speedX: -Math.sin(radians) * speed,
        speedY: Math.cos(radians) * speed,
        rotate: degrees,
        scaleY: { min: length[0]! / 8, max: length[1]! / 8 },
        alpha: { start: opacity, end: opacity * 0.6 },
        quantity: Math.max(1, Math.round((perEmitter * frequency) / lifespan)),
        frequency,
      }).setDepth(DEPTH.rain));
    }
    if (splashes && this.waterTiles.length > 0) {
      const tiles = this.waterTiles;
      this.lighting.push(this.add.particles(0, 0, 'fx:splash', {
        emitZone: {
          type: 'random',
          source: {
            getRandomPoint: (point: Phaser.Types.Math.Vector2Like) => {
              const tile = tiles[Math.floor(Math.random() * tiles.length)]!;
              point.x = (tile.x + Math.random()) * TILE;
              point.y = (tile.y + Math.random()) * TILE;
              return point;
            },
          },
        },
        lifespan: 280,
        scale: { start: 0.5, end: 1.3 },
        alpha: { start: Math.min(1, opacity * 2.5), end: 0 },
        frequency: Math.max(20, Math.round(4000 / tiles.length)),
      }).setDepth(DEPTH.rain));
    }
  }

  private buildSteam(): void {
    for (const o of this.objects.filter((obj) => obj.type === 'effect' && obj.props.effect === 'steam')) {
      this.add.particles(0, 0, 'fx:puff', {
        x: { min: o.x * TILE, max: (o.x + o.width) * TILE },
        y: { min: (o.y + o.height * 0.5) * TILE, max: (o.y + o.height) * TILE },
        lifespan: 2600,
        speedY: { min: -14, max: -6 },
        speedX: { min: -3, max: 3 },
        scale: { start: 0.5, end: 1.6 },
        alpha: { start: 0.22, end: 0 },
        frequency: 260,
      }).setDepth(DEPTH.steam);
    }
  }

  /**
   * While a dialogue is open the camera frames the player and the speaker. Where
   * the box covers the bottom `cover` share of the view, they sit in the middle of
   * the visible part above it; in the portrait deck nothing is covered (P-40).
   * When it closes, the camera follows the player again.
   */
  private frameDialogue(cover: number | null): void {
    const camera = this.cameras.main;
    if (cover === null) {
      this.focus = null;
      camera.startFollow(this.player, true, 0.2, 0.2);
      return;
    }
    const target = this.focus
      ? { x: (this.player.x + (this.focus.x + this.focus.width / 2) * TILE) / 2,
          y: (this.player.y + (this.focus.y + this.focus.height / 2) * TILE) / 2 }
      : { x: this.player.x, y: this.player.y };
    camera.stopFollow();
    camera.pan(target.x, target.y + (camera.height / camera.zoom) * (cover / 2), 350, 'Sine.easeInOut', true);
  }

  // ---------------------------------------------------------------------
  // camera and input

  private setUpCamera(): void {
    const camera = this.cameras.main;
    camera.setBounds(0, 0, this.mapWidth, this.mapHeight);
    camera.setRoundPixels(true);
    camera.startFollow(this.player, true, 0.2, 0.2);
    const fit = () => {
      const { width, height } = this.scale.gameSize;
      camera.setZoom(zoomFor({ width, height }, { width: this.mapWidth, height: this.mapHeight }));
      // The camera does not follow during a dialogue: frame the speaker again for the new size.
      if (this.bridge.dialogueCover !== null) this.frameDialogue(this.bridge.dialogueCover);
    };
    fit();
    this.scale.on(Phaser.Scale.Events.RESIZE, fit);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.scale.off(Phaser.Scale.Events.RESIZE, fit));
  }

  private setUpInput(): void {
    // Walk and interact on the key-down events themselves (P-51): a quick tap that goes down and
    // up within one long frame (a slow phone) would be lost to per-frame polling. No key capture or
    // default prevented: Space and Enter must still work on React's dialogue buttons.
    this.keyboard = new KeyboardInput(window, {
      press: (direction) => this.press(direction),
      interact: () => {
        if (this.canWalk()) this.interactQueued = true;
      },
    });
    const dispose = () => this.keyboard.dispose();
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, dispose);
    this.events.once(Phaser.Scenes.Events.DESTROY, dispose);
    this.input.on(Phaser.Input.Events.POINTER_DOWN, (pointer: Phaser.Input.Pointer) => {
      this.interactAt(pointer.worldX, pointer.worldY);
    });
  }
}

// ---------------------------------------------------------------------------
// helpers

function tilesetKey(name: string): string {
  return `tiles:${name}`;
}

function toMapObject(o: Phaser.Types.Tilemaps.TiledObject): MapObject {
  const props: Record<string, unknown> = {};
  for (const p of (o.properties as { name: string; value: unknown }[] | undefined) ?? []) props[p.name] = p.value;
  return {
    name: o.name ?? '',
    type: o.type ?? '',
    x: Math.round((o.x ?? 0) / TILE),
    y: Math.round((o.y ?? 0) / TILE),
    width: Math.max(1, Math.round((o.width ?? TILE) / TILE)),
    height: Math.max(1, Math.round((o.height ?? TILE) / TILE)),
    props,
  };
}

function covers(o: MapObject, tile: { x: number; y: number }): boolean {
  return tile.x >= o.x && tile.x < o.x + o.width && tile.y >= o.y && tile.y < o.y + o.height;
}

/** Chebyshev distance in tiles from a tile to the nearest tile of an object (as the server measures). */
function distance(o: MapObject, tile: { x: number; y: number }): number {
  const dx = Math.max(o.x - tile.x, tile.x - (o.x + o.width - 1), 0);
  const dy = Math.max(o.y - tile.y, tile.y - (o.y + o.height - 1), 0);
  return Math.max(dx, dy);
}

/** Chebyshev distance between two tiles. */
function chebyshev(a: Tile, b: Tile): number {
  return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
}

/** The facing from an object toward a tile (the way an NPC turns to look at the player). */
function facingToward(o: MapObject, tile: { x: number; y: number }): Facing {
  const dx = tile.x - (o.x + (o.width - 1) / 2);
  const dy = tile.y - (o.y + (o.height - 1) / 2);
  if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? 'LEFT' : 'RIGHT';
  return dy < 0 ? 'UP' : 'DOWN';
}

/** The tile under a pixel position. */
function tileAt(x: number, y: number): Tile {
  return { x: Math.floor(x / TILE), y: Math.floor(y / TILE) };
}

/** The pixel centre of a (possibly fractional, mid-step) tile position. */
function centreOf(tile: Tile): { x: number; y: number } {
  return { x: (tile.x + 0.5) * TILE, y: (tile.y + 0.5) * TILE };
}

function tileKey(tile: Tile): string {
  return `${tile.x},${tile.y}`;
}

const SHEET_COLUMNS = 4;

function idleFrame(facing: Facing): number {
  return sheetLayout.walkRows[0]! * SHEET_COLUMNS + sheetLayout.columns[facing];
}

function createWalkAnimations(scene: Phaser.Scene): void {
  for (const key of actorKeys) {
    for (const facing of Object.keys(sheetLayout.columns) as Facing[]) {
      const animation = `${key}:walk:${facing}`;
      if (scene.anims.exists(animation)) continue;
      scene.anims.create({
        key: animation,
        frames: sheetLayout.walkRows.map((row) => ({ key, frame: row * SHEET_COLUMNS + sheetLayout.columns[facing] })),
        frameRate: 8,
        repeat: -1,
      });
    }
  }
}

/** The multiply colour of the ambient light: ambient level times the state's tint. */
function ambientColour(state: LightState): number {
  const channel = (i: number) => Math.round(Phaser.Math.Clamp(state.ambient * (state.tint[i] ?? 1), 0, 1) * 255);
  return Phaser.Display.Color.GetColor(channel(0), channel(1), channel(2));
}

/** Small generated textures: a soft light, a rain streak, a splash ring and a steam puff. */
function createEffectTextures(scene: Phaser.Scene): void {
  if (!scene.textures.exists(LIGHT_TEXTURE)) {
    const canvas = scene.textures.createCanvas(LIGHT_TEXTURE, LIGHT_TEXTURE_SIZE, LIGHT_TEXTURE_SIZE)!;
    const ctx = canvas.getContext();
    const half = LIGHT_TEXTURE_SIZE / 2;
    const gradient = ctx.createRadialGradient(half, half, 0, half, half, half);
    gradient.addColorStop(0, 'rgba(255,255,255,1)');
    gradient.addColorStop(0.35, 'rgba(255,255,255,0.55)');
    gradient.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, LIGHT_TEXTURE_SIZE, LIGHT_TEXTURE_SIZE);
    canvas.refresh();
  }
  if (!scene.textures.exists('fx:rain')) {
    const g = scene.make.graphics({}, false);
    g.fillStyle(0xb4c4e0, 1).fillRect(0, 0, 1, 8);
    g.generateTexture('fx:rain', 1, 8);
    g.destroy();
  }
  if (!scene.textures.exists('fx:splash')) {
    const g = scene.make.graphics({}, false);
    g.lineStyle(1, 0xc8d4e8, 1).strokeEllipse(3, 2, 5, 3);
    g.generateTexture('fx:splash', 6, 4);
    g.destroy();
  }
  if (!scene.textures.exists('fx:puff')) {
    const canvas = scene.textures.createCanvas('fx:puff', 16, 16)!;
    const ctx = canvas.getContext();
    const gradient = ctx.createRadialGradient(8, 8, 0, 8, 8, 8);
    gradient.addColorStop(0, 'rgba(230,240,235,0.9)');
    gradient.addColorStop(1, 'rgba(230,240,235,0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 16, 16);
    canvas.refresh();
  }
}

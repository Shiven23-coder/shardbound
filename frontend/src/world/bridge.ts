// The typed event store between React and the Phaser world scene (§5).
// React sends the server's world view and corrections; the scene reports
// the world events the player causes. Neither side imports the other, and
// this file imports no Phaser, so it stays in the small initial bundle.
import type { BattleView, CutsceneActorView, CutsceneStepView, Facing, LastPosition, WorldView } from '../api/types';
import type { InputDevice } from '../input/inputMode';

export type Direction = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';

/** What the scene tells React: a world event at the player's position. */
export interface SceneEvents {
  exit: { exitId: string; x: number; y: number; facing: Facing };
  interact: { targetId: string; x: number; y: number; facing: Facing };
  /** The player came within a creep's aggro radius (V2_SPEC §4.2). */
  encounter: { creepId: string; x: number; y: number; facing: Facing };
  /** A creep is close: a battle may start soon (the battle music starts loading, P-57). */
  creepNear: undefined;
  /** Something the player can use came into reach (its kind), or nothing is in reach (null). */
  reach: 'npc' | 'lantern' | 'object' | null;
  /** The player stepped within a tile of their Lost Shards (P-62). */
  recover: { x: number; y: number; facing: Facing };
  /** The player took their first step in this scene. */
  moved: undefined;
  /** The player walked under the HUD (true) or out from under it (false). */
  hudCovered: boolean;
  /** The scene finished staging the cutscene step it was given (P-68). */
  cutsceneStepDone: undefined;
}

/** A cutscene's cast (P-68): the scene places these sprites and stages its steps with them. */
export interface CutsceneCast {
  id: string;
  actors: CutsceneActorView[];
}

/** A rectangle in CSS pixels, relative to the game view's top-left corner. */
export interface ScreenRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** What React tells the scene. */
export interface ReactCommands {
  /** The server's latest world (area, light, player, NPCs, hidden objects). */
  view: WorldView;
  /** The server rejected an event: put the player back here. */
  snapBack: LastPosition;
  /** Walking is paused (a dialogue, a fight, a request in flight). */
  frozen: boolean;
  /** The touch interact button was pressed. */
  interact: undefined;
  /** A touch D-pad button went down: one queued step or turn (P-51). */
  press: Direction;
  /** A dialogue opened, covering this share of the bottom of the view, or closed (null). */
  dialogue: number | null;
  /** A battle to show (each new round), or null once it is over; `cover` is the share of the view under the text box. */
  battle: { battle: BattleView; cover: number } | null;
  /** The round's text line now on screen (Infinity once read): the arena plays the hits up to it. */
  battleLine: BattleLine;
  /** A cutscene starts with this cast, or the one playing ends (null, P-68). */
  cutscene: CutsceneCast | null;
  /** A step for the scene to stage; it answers cutsceneStepDone. */
  cutsceneStep: CutsceneStepView;
}

export interface BattleLine {
  round: number;
  line: number;
}

type Listener<T> = (payload: T) => void;

/** A minimal typed event emitter. */
export class Channel<Events> {
  private readonly listeners = new Map<keyof Events, Set<Listener<never>>>();

  /** Subscribes; returns the function that unsubscribes. */
  on<K extends keyof Events>(name: K, listener: Listener<Events[K]>): () => void {
    const set = this.listeners.get(name) ?? new Set();
    set.add(listener as Listener<never>);
    this.listeners.set(name, set);
    return () => set.delete(listener as Listener<never>);
  }

  emit<K extends keyof Events>(name: K, payload: Events[K]): void {
    for (const listener of this.listeners.get(name) ?? []) {
      (listener as Listener<Events[K]>)(payload);
    }
  }
}

/**
 * One bridge per game screen. The scene reads {@link direction} every frame
 * while it is held; each D-pad press is also sent as a `press` command, so a
 * quick tap is never lost between frames (P-51).
 */
export class WorldBridge {
  readonly toScene = new Channel<ReactCommands>();
  readonly toReact = new Channel<SceneEvents>();
  /** The direction held on the touch D-pad, or null. */
  direction: Direction | null = null;
  /** The last view sent, for a scene that starts after it was sent. */
  latestView: WorldView | null = null;
  frozen = true;
  /** Where the HUD sits on the game view: the player must never be hidden under it (P-39). */
  hudArea: ScreenRect | null = null;
  /** The device in use, so the scene's interaction marker names its button (P-46). */
  device: InputDevice = 'keyboard';
  /** The share of the view a dialogue covers, or null while none is open. */
  dialogueCover: number | null = null;
  /** The battle on screen, for a scene that starts after it was sent. */
  latestBattle: { battle: BattleView; cover: number } | null = null;
  /** The battle text line on screen, for a scene that gets the round after the line was sent. */
  latestBattleLine: BattleLine = { round: -1, line: 0 };
  /** The cutscene playing, for a scene that starts after it began. */
  latestCutscene: CutsceneCast | null = null;

  sendView(view: WorldView): void {
    this.latestView = view;
    this.toScene.emit('view', view);
  }

  setFrozen(frozen: boolean): void {
    this.frozen = frozen;
    this.toScene.emit('frozen', frozen);
  }

  setBattle(battle: BattleView | null, cover: number): void {
    const next = battle ? { battle, cover } : null;
    if (next?.battle === this.latestBattle?.battle && next?.cover === this.latestBattle?.cover) return;
    this.latestBattle = next;
    this.toScene.emit('battle', next);
  }

  setBattleLine(round: number, line: number): void {
    if (round === this.latestBattleLine.round && line === this.latestBattleLine.line) return;
    this.latestBattleLine = { round, line };
    this.toScene.emit('battleLine', this.latestBattleLine);
  }

  setCutscene(cast: CutsceneCast | null): void {
    if (cast?.id === this.latestCutscene?.id) return;
    this.latestCutscene = cast;
    this.toScene.emit('cutscene', cast);
  }

  setDialogue(cover: number | null): void {
    if (cover === this.dialogueCover) return;
    this.dialogueCover = cover;
    this.toScene.emit('dialogue', cover);
  }
}

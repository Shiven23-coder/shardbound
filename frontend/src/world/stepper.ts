// Tile-step movement (P-51). The player always stands on a whole tile; a step
// slides to the next tile in `stepMs`. Every press is queued and becomes one
// step (or a turn), so a press that goes down and up inside one long frame
// still counts; a held direction keeps walking. Time carries over from one
// step to the next, so the speed and the tiles reached do not depend on the
// frame rate. No Phaser here.
import type { Direction } from './bridge';

export interface Tile {
  x: number;
  y: number;
}

export interface StepTiming {
  /** Milliseconds per one-tile step. */
  stepMs: number;
  /** How long a new direction must be held, standing still, before the player walks (a shorter tap only turns). */
  turnMs: number;
}

/** What the stepper asks of the world. */
export interface StepWorld {
  /** A tile the player cannot enter. */
  blocked(tile: Tile): boolean;
  /** The player arrived on a tile; returning true stops the walk there (an exit or a trigger fired). */
  arrived(tile: Tile): boolean;
  /** A step toward `to` began. */
  stepStarted?(to: Tile): void;
}

/** The longest frame the stepper advances in one go; a longer hitch counts as this long. */
export const MAX_FRAME_MS = 250;

const STEP: Record<Direction, Tile> = { UP: { x: 0, y: -1 }, DOWN: { x: 0, y: 1 }, LEFT: { x: -1, y: 0 }, RIGHT: { x: 1, y: 0 } };

export function nextTile(tile: Tile, direction: Direction): Tile {
  return { x: tile.x + STEP[direction].x, y: tile.y + STEP[direction].y };
}

export class Stepper {
  /** The tile the player stands on; during a step, the tile it started from. */
  tile: Tile;
  facing: Direction;
  private target: Tile | null = null;
  private elapsed = 0;
  private turnLeft = 0;
  private readonly presses: Direction[] = [];

  constructor(tile: Tile, facing: Direction, private readonly timing: StepTiming) {
    this.tile = { ...tile };
    this.facing = facing;
  }

  /** A step is under way. */
  get walking(): boolean {
    return this.target !== null;
  }

  /** Where to draw the player, in tiles (fractional during a step). */
  get drawn(): Tile {
    if (!this.target) return { ...this.tile };
    const t = this.elapsed / this.timing.stepMs;
    return { x: this.tile.x + (this.target.x - this.tile.x) * t, y: this.tile.y + (this.target.y - this.tile.y) * t };
  }

  /** Queues a press: one step in that direction, or a turn toward it. */
  press(direction: Direction): void {
    this.presses.push(direction);
  }

  /** Forgets the queued presses; a step under way still finishes. */
  halt(): void {
    this.presses.length = 0;
    this.turnLeft = 0;
  }

  /** Puts the player on a tile at once, facing `facing` (a snap-back from the server). */
  place(tile: Tile, facing: Direction): void {
    this.halt();
    this.tile = { ...tile };
    this.facing = facing;
    this.target = null;
    this.elapsed = 0;
  }

  /** Advances by `dtMs` of real time with `held` held (or nothing held). */
  update(dtMs: number, held: Direction | null, world: StepWorld): void {
    let time = Math.min(dtMs, MAX_FRAME_MS);
    while (this.target) {
      const left = this.timing.stepMs - this.elapsed;
      if (time < left) {
        this.elapsed += time;
        return;
      }
      time -= left;
      this.tile = this.target;
      this.target = null;
      this.elapsed = 0;
      if (world.arrived(this.tile)) {
        this.halt();
        return;
      }
      // Walking on: the leftover time of this frame goes into the next step.
      if (!this.begin(held, world, true)) return;
    }
    if (this.turnLeft > 0) {
      if (time < this.turnLeft) {
        this.turnLeft -= time;
        return;
      }
      this.turnLeft = 0;
    }
    // Standing still: a step that starts now runs from the next frame on.
    this.begin(held, world, false);
  }

  /** Starts the next step (or turn) from the queue, else the held direction. True if a step started. */
  private begin(held: Direction | null, world: StepWorld, moving: boolean): boolean {
    const direction = this.presses.shift() ?? held;
    if (!direction) return false;
    if (direction !== this.facing && !moving) {
      this.facing = direction;
      this.turnLeft = this.timing.turnMs;
      return false;
    }
    this.facing = direction;
    const to = nextTile(this.tile, direction);
    if (world.blocked(to)) return false;
    this.target = to;
    world.stepStarted?.(to);
    return true;
  }
}

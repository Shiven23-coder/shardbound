import { describe, expect, it } from 'vitest';
import type { Direction } from './bridge';
import { MAX_FRAME_MS, Stepper, type StepWorld, type Tile } from './stepper';

const TIMING = { stepMs: 160, turnMs: 100 };

/** A small open field with walls where the test puts them; records arrivals. */
function field(walls: Tile[] = [], stopsAt: Tile[] = []): StepWorld & { arrivals: Tile[]; started: number } {
  const world = {
    arrivals: [] as Tile[],
    started: 0,
    blocked: (t: Tile) => t.x < 0 || t.y < 0 || t.x >= 20 || t.y >= 20 || walls.some((w) => w.x === t.x && w.y === t.y),
    arrived: (t: Tile) => {
      world.arrivals.push(t);
      return stopsAt.some((s) => s.x === t.x && s.y === t.y);
    },
    stepStarted: () => {
      world.started++;
    },
  };
  return world;
}

/** Runs the stepper for `ms` in frames of `frameMs`, with `held` held throughout. */
function run(stepper: Stepper, world: StepWorld, ms: number, frameMs: number, held: Direction | null): void {
  for (let t = 0; t < ms; t += frameMs) stepper.update(Math.min(frameMs, ms - t), held, world);
}

describe('tile steps (P-51)', () => {
  it('a press in the facing direction moves exactly one tile, smoothly, in stepMs', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    s.press('RIGHT');
    s.update(16, null, world);                                   // the step starts on this frame
    expect(s.walking).toBe(true);
    run(s, world, 80, 16, null);
    expect(s.tile).toEqual({ x: 5, y: 5 });                       // still on its tile until it arrives
    expect(s.drawn.x).toBeGreaterThan(5.4);
    expect(s.drawn.x).toBeLessThan(5.6);
    expect(s.drawn.y).toBe(5);
    run(s, world, 100, 16, null);
    expect(s.tile).toEqual({ x: 6, y: 5 });
    expect(s.drawn).toEqual({ x: 6, y: 5 });
    expect(s.walking).toBe(false);
    run(s, world, 1000, 16, null);
    expect(s.tile, 'one press, one tile').toEqual({ x: 6, y: 5 });
  });

  it('holding a direction keeps walking at one tile per stepMs', () => {
    const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    s.update(16, 'RIGHT', world);
    run(s, world, 160 * 10, 16, 'RIGHT');
    expect(s.tile).toEqual({ x: 10, y: 5 });
  });

  it('walks at the same speed and reaches the same tile at any frame rate', () => {
    const tiles = [8, 16, 33, 50, 100, 250].map((frameMs) => {
      const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
      const world = field();
      s.update(frameMs, 'RIGHT', world);
      run(s, world, 160 * 9 + 40, frameMs, 'RIGHT');
      return s.tile.x;
    });
    expect(new Set(tiles)).toEqual(new Set([9]));
  });

  it('a long hitch counts as at most MAX_FRAME_MS', () => {
    const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    s.update(16, 'RIGHT', world);
    s.update(5000, 'RIGHT', world);
    expect(s.tile.x).toBe(Math.floor(MAX_FRAME_MS / TIMING.stepMs));
  });

  it('a quick tap on a new direction only turns in place', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'DOWN', TIMING);
    const world = field();
    s.press('LEFT');
    s.update(16, 'LEFT', world);
    expect(s.facing).toBe('LEFT');
    run(s, world, 48, 16, 'LEFT');                                // released before turnMs
    run(s, world, 1000, 16, null);
    expect(s.tile).toEqual({ x: 5, y: 5 });
    expect(world.started).toBe(0);
  });

  it('holding a new direction turns, then walks once turnMs has passed', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'DOWN', TIMING);
    const world = field();
    s.press('LEFT');
    s.update(16, 'LEFT', world);
    run(s, world, TIMING.turnMs + 16 + TIMING.stepMs, 16, 'LEFT');
    expect(s.tile).toEqual({ x: 4, y: 5 });
  });

  it('while walking, a new direction turns and steps at once', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    s.update(16, 'RIGHT', world);
    run(s, world, 150, 10, 'RIGHT');                              // 10 ms short of the first arrival
    run(s, world, 10 + TIMING.stepMs, 10, 'DOWN');
    expect(s.tile).toEqual({ x: 6, y: 6 });
  });

  it('a press that goes down and up within one frame still counts', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'UP', TIMING);
    const world = field();
    s.press('UP');                                                // released before the frame: held is null
    run(s, world, 600, 200, null);
    expect(s.tile).toEqual({ x: 5, y: 4 });
  });

  it('presses queue: one step (or turn) each, none lost while a step is under way', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'DOWN', TIMING);
    const world = field();
    for (const d of ['RIGHT', 'RIGHT', 'RIGHT', 'UP', 'UP'] as Direction[]) s.press(d);
    run(s, world, 2000, 120, null);
    // RIGHT turns (standing still), two RIGHT steps, then UP turns and steps at once (walking), then one more UP.
    expect(s.tile).toEqual({ x: 7, y: 3 });
    expect(s.facing).toBe('UP');
  });

  it('a blocked tile only turns the player toward it', () => {
    const s = new Stepper({ x: 5, y: 5 }, 'DOWN', TIMING);
    const world = field([{ x: 5, y: 4 }]);
    s.press('UP');
    s.press('UP');
    run(s, world, 1000, 16, 'UP');
    expect(s.tile).toEqual({ x: 5, y: 5 });
    expect(s.facing).toBe('UP');
    expect(world.started).toBe(0);
  });

  it('never enters a blocked tile while holding, at any frame rate', () => {
    for (const frameMs of [16, 100, 250]) {
      const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
      const world = field([{ x: 4, y: 5 }]);
      const seen: number[] = [];
      for (let t = 0; t < 3000; t += frameMs) {
        s.update(frameMs, 'RIGHT', world);
        seen.push(s.tile.x, s.drawn.x);
      }
      expect(Math.max(...seen), `${frameMs} ms frames`).toBe(3);
    }
  });

  it('stops on a tile whose arrival starts an event, and drops the queued presses', () => {
    const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
    const world = field([], [{ x: 2, y: 5 }]);
    for (let i = 0; i < 6; i++) s.press('RIGHT');
    run(s, world, 2000, 16, null);
    expect(s.tile).toEqual({ x: 2, y: 5 });
    expect(world.arrivals).toEqual([{ x: 1, y: 5 }, { x: 2, y: 5 }]);
  });

  it('halt finishes the step under way but starts no other, and forgets the queue', () => {
    const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    for (let i = 0; i < 4; i++) s.press('RIGHT');
    s.update(16, null, world);
    s.halt();
    run(s, world, 1000, 16, null);
    expect(s.tile).toEqual({ x: 1, y: 5 });
  });

  it('place puts the player on a tile at once (a server snap-back)', () => {
    const s = new Stepper({ x: 0, y: 5 }, 'RIGHT', TIMING);
    const world = field();
    s.press('RIGHT');
    s.update(16, 'RIGHT', world);
    s.place({ x: 9, y: 9 }, 'LEFT');
    expect(s.walking).toBe(false);
    expect(s.drawn).toEqual({ x: 9, y: 9 });
    run(s, world, 1000, 16, null);
    expect(s.tile).toEqual({ x: 9, y: 9 });
    expect(s.facing).toBe('LEFT');
  });
});

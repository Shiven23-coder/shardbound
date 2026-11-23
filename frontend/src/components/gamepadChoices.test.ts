import { describe, expect, it } from 'vitest';
import { choiceIntent, padState } from './gamepadChoices';

function pad(pressed: number[], axes: number[] = [0, 0]) {
  const buttons = Array.from({ length: 17 }, (_, i) => ({ pressed: pressed.includes(i) }));
  return { buttons, axes } as unknown as Gamepad;
}

const idle = padState(pad([]));

describe('gamepad choices', () => {
  it('reads A, the D-pad and the left stick', () => {
    expect(padState(pad([0]))).toEqual({ up: false, down: false, left: false, right: false, confirm: true, back: false });
    expect(padState(pad([1])).back).toBe(true);
    expect(padState(pad([12])).up).toBe(true);
    expect(padState(pad([13])).down).toBe(true);
    expect(padState(pad([], [0, -0.9])).up).toBe(true);
    expect(padState(pad([], [0, 0.3])).down).toBe(false);
  });

  it('acts only on a fresh press', () => {
    const a = padState(pad([0]));
    expect(choiceIntent(idle, a)).toBe('confirm');
    expect(choiceIntent(a, a)).toBeNull();
    expect(choiceIntent(idle, padState(pad([13])))).toBe('down');
    expect(choiceIntent(idle, padState(pad([1])))).toBe('back');
    expect(choiceIntent(idle, padState(pad([14])))).toBe('left');            // − on the level-up screen (P-61)
    expect(choiceIntent(idle, padState(pad([], [0.9, 0])))).toBe('right');
  });
});

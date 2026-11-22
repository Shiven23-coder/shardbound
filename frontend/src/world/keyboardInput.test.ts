import { describe, expect, it } from 'vitest';
import type { Direction } from './bridge';
import { KeyboardInput } from './keyboardInput';

function key(type: 'keydown' | 'keyup', k: string, extra: Partial<{ repeat: boolean }> = {}): Event {
  return Object.assign(new Event(type), { key: k, repeat: false, ...extra });
}

function setUp() {
  const target = new EventTarget();
  const presses: Direction[] = [];
  let interacts = 0;
  const input = new KeyboardInput(target, { press: (d) => presses.push(d), interact: () => interacts++ });
  return { target, presses, input, interacts: () => interacts };
}

describe('keyboard input (P-51)', () => {
  it('every key-down is exactly one press, even many within one frame', () => {
    const { target, presses, input } = setUp();
    for (let i = 0; i < 5; i++) {
      target.dispatchEvent(key('keydown', 'ArrowRight'));
      target.dispatchEvent(key('keyup', 'ArrowRight'));
    }
    expect(presses).toEqual(['RIGHT', 'RIGHT', 'RIGHT', 'RIGHT', 'RIGHT']);
    expect(input.held()).toBeNull();
  });

  it('arrows and WASD walk; auto-repeat adds no press', () => {
    const { target, presses, input } = setUp();
    target.dispatchEvent(key('keydown', 'w'));
    target.dispatchEvent(key('keydown', 'w', { repeat: true }));
    expect(presses).toEqual(['UP']);
    expect(input.held()).toBe('UP');
  });

  it('the direction pressed last wins while several are held, and the earlier one returns on release', () => {
    const { target, input } = setUp();
    target.dispatchEvent(key('keydown', 'ArrowUp'));
    target.dispatchEvent(key('keydown', 'D'));
    expect(input.held()).toBe('RIGHT');
    target.dispatchEvent(key('keyup', 'D'));
    expect(input.held()).toBe('UP');
  });

  it('E, Space and Enter interact', () => {
    const { target, interacts } = setUp();
    for (const k of ['e', ' ', 'Enter']) target.dispatchEvent(key('keydown', k));
    expect(interacts()).toBe(3);
  });

  it('losing focus releases every key; dispose stops listening', () => {
    const { target, presses, input } = setUp();
    target.dispatchEvent(key('keydown', 'ArrowLeft'));
    target.dispatchEvent(new Event('blur'));
    expect(input.held()).toBeNull();
    input.dispose();
    target.dispatchEvent(key('keydown', 'ArrowLeft'));
    expect(presses).toEqual(['LEFT']);
  });
});

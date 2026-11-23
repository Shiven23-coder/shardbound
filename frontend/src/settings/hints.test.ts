import { beforeEach, describe, expect, it, vi } from 'vitest';
import { hintText, markHintSeen, seenHints } from './hints';

/** The tests run in Node: a small in-memory localStorage stands in for the browser's. */
function memoryStorage(): Pick<Storage, 'getItem' | 'setItem'> {
  const items = new Map<string, string>();
  return { getItem: (key) => items.get(key) ?? null, setItem: (key, value) => void items.set(key, value) };
}

describe('hints (P-41, P-61)', () => {
  beforeEach(() => vi.stubGlobal('localStorage', memoryStorage()));

  it('names the button of the device in use, for the Lantern too', () => {
    expect(hintText('lantern', 'keyboard')).toBe('Press E to rest at the Lantern.');
    expect(hintText('lantern', 'gamepad')).toBe('Press A to rest at the Lantern.');
    expect(hintText('lantern', 'touch')).toBe('Tap Ⓐ to rest at the Lantern.');
    expect(hintText('interact', 'keyboard', true)).toBe('Press E to talk.');
  });

  it('explains levelling and resting at the first Lantern menu', () => {
    expect(hintText('levelup', 'touch')).toContain('Spend Shards here');
  });

  it('remembers every hint seen, per profile', () => {
    markHintSeen(7, 'lantern');
    markHintSeen(7, 'levelup');
    expect([...seenHints(7)].sort()).toEqual(['lantern', 'levelup']);
    expect(seenHints(8).size).toBe(0);
  });
});

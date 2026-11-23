import type { InputDevice } from '../input/inputMode';

// Contextual hints (P-41): each shows once per profile, can be skipped, and
// names the button of the device in use.

export type HintId = 'move' | 'interact' | 'lantern' | 'levelup';

const HINT_IDS: readonly HintId[] = ['move', 'interact', 'lantern', 'levelup'];

const key = (playerId: number) => `shardbound.hints.${playerId}`;

export function seenHints(playerId: number): Set<HintId> {
  try {
    const stored = JSON.parse(localStorage.getItem(key(playerId)) ?? '[]');
    return new Set(Array.isArray(stored) ? stored.filter((h): h is HintId => HINT_IDS.includes(h)) : []);
  } catch {
    return new Set();
  }
}

export function markHintSeen(playerId: number, hint: HintId): void {
  const seen = seenHints(playerId);
  seen.add(hint);
  try {
    localStorage.setItem(key(playerId), JSON.stringify([...seen]));
  } catch {
    // Storage unavailable: the hint may show again next visit.
  }
}

/** The text of a hint for the device in use; `talk` says whether the thing in reach is a person. */
export function hintText(hint: HintId, device: InputDevice, talk = false): string {
  if (hint === 'levelup') {
    return 'Spend Shards here to grow stronger. Resting heals you and refills your draught, '
      + 'but the creatures you have beaten return.';
  }
  const verb = hint === 'lantern' ? 'rest at the Lantern' : talk ? 'talk' : 'look';
  if (hint === 'move') {
    if (device === 'gamepad') return 'Walk with the left stick or the D-pad.';
    if (device === 'touch') return 'Hold the arrows to walk.';
    return 'Walk with WASD or the arrow keys.';
  }
  if (device === 'gamepad') return `Press A to ${verb}.`;
  if (device === 'touch') return `Tap Ⓐ to ${verb}.`;
  return `Press E to ${verb}.`;
}

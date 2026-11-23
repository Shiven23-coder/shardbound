// The Lantern menu (P-61): LEVEL UP · SKILLS · LEAVE, and the Skills page
// where a loadout slot is chosen, then the skill to put in it. Pure, so the
// keyboard, the gamepad and taps share one set of rules.
import type { Intent } from '../dialogue/dialogueInput';

export type LanternPage = 'root' | 'level' | 'skills';

export const ROOT_ITEMS = ['level', 'skills', 'leave'] as const;
export type RootItem = (typeof ROOT_ITEMS)[number];

/** On the root menu: the highlight moves, E or a number opens an entry, Esc leaves the Lantern. */
export function rootStep(highlight: number, intent: Intent): { highlight: number; open?: RootItem } {
  switch (intent.kind) {
    case 'up':
      return { highlight: Math.max(0, highlight - 1) };
    case 'down':
      return { highlight: Math.min(ROOT_ITEMS.length - 1, highlight + 1) };
    case 'primary':
      return { highlight, open: ROOT_ITEMS[highlight] };
    case 'pick':
      return intent.index < ROOT_ITEMS.length ? { highlight: intent.index, open: ROOT_ITEMS[intent.index] } : { highlight };
    case 'back':
      return { highlight, open: 'leave' };
    case 'left':
    case 'right':
      return { highlight };
  }
}

/**
 * The Skills page: rows are the four slots, then Back. Choosing a slot opens
 * the list of known skills plus "Empty"; choosing one of those fills the slot.
 */
export interface LoadoutState {
  /** The slot being filled, or null while choosing a slot. */
  slot: number | null;
  highlight: number;
}

export const LOADOUT_START: LoadoutState = { slot: null, highlight: 0 };
export const SLOTS = 4;

export type LoadoutAction = { kind: 'set'; slot: number; skillId: string | null } | { kind: 'back' };

/** `skills` are the known skill ids; the chooser lists them, then Empty. */
export function loadoutStep(
  state: LoadoutState, intent: Intent, skills: string[],
): { state: LoadoutState; action?: LoadoutAction } {
  const rows = state.slot === null ? SLOTS + 1 : skills.length + 1;     // + Back, or + Empty
  switch (intent.kind) {
    case 'up':
      return { state: { ...state, highlight: Math.max(0, state.highlight - 1) } };
    case 'down':
      return { state: { ...state, highlight: Math.min(rows - 1, state.highlight + 1) } };
    case 'back':
      return state.slot === null ? { state, action: { kind: 'back' } } : { state: { slot: null, highlight: state.slot } };
    case 'pick':
    case 'primary': {
      const index = intent.kind === 'pick' ? intent.index : state.highlight;
      if (index >= rows) return { state };
      if (state.slot === null) {
        return index === SLOTS ? { state, action: { kind: 'back' } } : { state: { slot: index, highlight: 0 } };
      }
      const skillId = index < skills.length ? skills[index]! : null;
      return { state: { slot: null, highlight: state.slot }, action: { kind: 'set', slot: state.slot, skillId } };
    }
    case 'left':
    case 'right':
      return { state };
  }
}

/** The loadout with `skillId` in `slot`; a skill already elsewhere moves (each skill once, P-61). */
export function placed(loadout: (string | null)[], slot: number, skillId: string | null): (string | null)[] {
  return loadout.map((current, i) => {
    if (i === slot) return skillId;
    return skillId !== null && current === skillId ? null : current;
  });
}

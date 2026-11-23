// The level-up screen (P-61): six attribute rows, then Confirm and Back.
// + and − change the pending points at once; the server's preview follows
// when it answers, and an answer to an older request is ignored. Pure, so
// the keyboard, the gamepad and taps share one set of rules.
import type { Intent } from '../dialogue/dialogueInput';
import type { AttributeId, Raises } from '../api/types';

export interface AttributeInfo {
  id: AttributeId;
  name: string;
  /** What raising it does, in a few words. */
  effect: string;
}

/** The attributes in menu order, with what each one does (V2_SPEC §4.3). */
export const ATTRIBUTES: AttributeInfo[] = [
  { id: 'VIGOR', name: 'Vigor', effect: 'Max HP' },
  { id: 'MIND', name: 'Mind', effect: 'Max FP · Fire, Frost, Lightning' },
  { id: 'STRENGTH', name: 'Strength', effect: 'Strength weapon scaling' },
  { id: 'DEXTERITY', name: 'Dexterity', effect: 'Speed · crit · Dex scaling' },
  { id: 'ENDURANCE', name: 'Endurance', effect: 'Defence · status resistance' },
  { id: 'ARCANE', name: 'Arcane', effect: 'Holy, Crystal · status chance' },
];

/** The rows after the attributes. */
export const CONFIRM_ROW = ATTRIBUTES.length;
export const BACK_ROW = ATTRIBUTES.length + 1;

export interface LevelUpState {
  /** Points waiting to be bought, per attribute. */
  pending: Partial<Record<AttributeId, number>>;
  /** The highlighted row: an attribute, Confirm or Back. */
  row: number;
}

export const START: LevelUpState = { pending: {}, row: 0 };

export type LevelUpAction = 'confirm' | 'back';

/** Changes one attribute's pending points by `by`, between 0 and what the cap leaves. */
export function adjust(state: LevelUpState, id: AttributeId, by: number, value: number, max: number): LevelUpState {
  const next = Math.max(0, Math.min(max - value, (state.pending[id] ?? 0) + by));
  return { ...state, pending: { ...state.pending, [id]: next } };
}

/** The points to buy, leaving out attributes with none. */
export function raisesOf(state: LevelUpState): Raises {
  const raises: Raises = {};
  for (const { id } of ATTRIBUTES) {
    const points = state.pending[id] ?? 0;
    if (points > 0) raises[id] = points;
  }
  return raises;
}

export function pendingLevels(state: LevelUpState): number {
  return Object.values(state.pending).reduce((sum, points) => sum + (points ?? 0), 0);
}

/**
 * What an intent does on the level-up screen. W/S or ↑/↓ move between rows;
 * A/D or ←/→ (and the D-pad's sides) are − and + on an attribute; E on an
 * attribute adds a point, on Confirm buys the levels, on Back leaves; Esc leaves.
 */
export function levelUpStep(
  state: LevelUpState, intent: Intent, values: Record<AttributeId, number>, max: number,
): { state: LevelUpState; action?: LevelUpAction } {
  const attribute = ATTRIBUTES[state.row];
  switch (intent.kind) {
    case 'up':
      return { state: { ...state, row: Math.max(0, state.row - 1) } };
    case 'down':
      return { state: { ...state, row: Math.min(BACK_ROW, state.row + 1) } };
    case 'left':
    case 'right':
      return attribute
        ? { state: adjust(state, attribute.id, intent.kind === 'left' ? -1 : 1, values[attribute.id], max) }
        : { state };
    case 'primary':
      if (attribute) return { state: adjust(state, attribute.id, 1, values[attribute.id], max) };
      if (state.row === CONFIRM_ROW) return pendingLevels(state) > 0 ? { state, action: 'confirm' } : { state };
      return { state, action: 'back' };
    case 'back':
      return { state, action: 'back' };
    case 'pick':
      return { state };
  }
}

/**
 * Hands out request numbers and tells whether an answer belongs to the newest
 * request: the preview shown is never one the player has already changed.
 */
export function latestRequest() {
  let newest = 0;
  return {
    next: () => ++newest,
    isLatest: (id: number) => id === newest,
  };
}

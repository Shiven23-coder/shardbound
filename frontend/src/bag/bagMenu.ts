// The Bag (P-65): the item list, and Compare for a weapon before it is equipped.
// Pure, so the keyboard, the gamepad and taps share one set of rules.
import type { Intent } from '../dialogue/dialogueInput';
import type { WeaponView } from '../api/types';

/** A row of the list: what the menu needs to know about an item. */
export interface BagRow {
  id: string;
  weapon: boolean;
  equipped: boolean;
  usable: boolean;
}

/** The list, or Compare for `itemId` with its buttons Equip (0) and Back (1). */
export type BagState =
  | { page: 'list'; highlight: number }
  | { page: 'compare'; highlight: number; itemId: string };

export const BAG_START: BagState = { page: 'list', highlight: 0 };

export type BagAction =
  | { kind: 'use'; itemId: string }
  | { kind: 'equip'; itemId: string }
  | { kind: 'tab'; delta: -1 | 1 }
  | { kind: 'close' };

const COMPARE_BUTTONS = 2;

export function bagStep(state: BagState, intent: Intent, rows: BagRow[]): { state: BagState; action?: BagAction } {
  return state.page === 'list' ? listStep(state.highlight, intent, rows) : compareStep(state, intent, rows);
}

function listStep(current: number, intent: Intent, rows: BagRow[]): { state: BagState; action?: BagAction } {
  const last = Math.max(0, rows.length - 1);
  const highlight = Math.min(current, last);
  switch (intent.kind) {
    case 'up':
      return { state: { page: 'list', highlight: Math.max(0, highlight - 1) } };
    case 'down':
      return { state: { page: 'list', highlight: Math.min(last, highlight + 1) } };
    case 'left':
    case 'right':
      return { state: { page: 'list', highlight }, action: { kind: 'tab', delta: intent.kind === 'left' ? -1 : 1 } };
    case 'back':
      return { state: { page: 'list', highlight }, action: { kind: 'close' } };
    case 'pick':
    case 'primary': {
      const index = intent.kind === 'pick' ? intent.index : highlight;
      const row = rows[index];
      if (!row) return { state: { page: 'list', highlight } };
      if (row.weapon && !row.equipped) return { state: { page: 'compare', highlight: 0, itemId: row.id } };
      if (row.usable) return { state: { page: 'list', highlight: index }, action: { kind: 'use', itemId: row.id } };
      return { state: { page: 'list', highlight: index } };
    }
  }
}

function compareStep(
  state: Extract<BagState, { page: 'compare' }>, intent: Intent, rows: BagRow[],
): { state: BagState; action?: BagAction } {
  const toList: BagState = { page: 'list', highlight: Math.max(0, rows.findIndex((r) => r.id === state.itemId)) };
  switch (intent.kind) {
    case 'up':
    case 'left':
      return { state: { ...state, highlight: Math.max(0, state.highlight - 1) } };
    case 'down':
    case 'right':
      return { state: { ...state, highlight: Math.min(COMPARE_BUTTONS - 1, state.highlight + 1) } };
    case 'back':
      return { state: toList };
    case 'pick':
    case 'primary': {
      const button = intent.kind === 'pick' ? intent.index : state.highlight;
      if (button === 0) return { state: toList, action: { kind: 'equip', itemId: state.itemId } };
      return button === 1 ? { state: toList } : { state };
    }
  }
}

/** One line of Compare: the equipped weapon's value, the candidate's, and which way it goes. */
export interface CompareRow {
  label: string;
  before: string;
  after: string;
  change: 'better' | 'worse' | 'same';
}

/** Scaling grades from best to none (V2_SPEC §4.6). */
const GRADE_ORDER = ['S', 'A', 'B', 'C', 'D', 'E'];
const NONE = '–';

function gradeRank(grade: string | null | undefined): number {
  const rank = grade ? GRADE_ORDER.indexOf(grade) : -1;
  return rank < 0 ? GRADE_ORDER.length : rank;
}

function change(gain: number): CompareRow['change'] {
  return gain > 0 ? 'better' : gain < 0 ? 'worse' : 'same';
}

/** The server's numbers for the equipped weapon (null: bare hands) beside the candidate's. */
export function compareRows(current: WeaponView | null, candidate: WeaponView): CompareRow[] {
  const grades = (['str', 'dex', 'arc'] as const).map((key) => {
    const before = current?.[key] ?? null;
    const after = candidate[key] ?? null;
    return {
      label: key === 'str' ? 'Str' : key === 'dex' ? 'Dex' : 'Arc',
      before: before ?? NONE,
      after: after ?? NONE,
      change: change(gradeRank(before) - gradeRank(after)),
    };
  });
  return [
    {
      label: 'AR', before: current ? String(current.attack) : NONE, after: String(candidate.attack),
      change: current ? change(candidate.attack - current.attack) : 'better',
    },
    {
      label: 'Crit', before: current ? `${current.critPct}%` : NONE, after: `${candidate.critPct}%`,
      change: change(candidate.critPct - (current?.critPct ?? 0)),
    },
    ...grades,
  ];
}

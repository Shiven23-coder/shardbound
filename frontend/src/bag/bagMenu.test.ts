import { describe, expect, it } from 'vitest';
import { BAG_START, bagStep, compareRows, type BagRow } from './bagMenu';
import type { WeaponView } from '../api/types';

const ROWS: BagRow[] = [
  { id: 'woodcutter_hatchet', weapon: true, equipped: false, usable: false },
  { id: 'rusty_shortsword', weapon: true, equipped: true, usable: false },
  { id: 'lantern_draught', weapon: false, equipped: false, usable: true },
  { id: 'heartshard', weapon: false, equipped: false, usable: false },
];

describe('the Bag menu', () => {
  it('moves the highlight within the list', () => {
    expect(bagStep(BAG_START, { kind: 'up' }, ROWS).state.highlight).toBe(0);
    const down = bagStep(bagStep(BAG_START, { kind: 'down' }, ROWS).state, { kind: 'down' }, ROWS).state;
    expect(down.highlight).toBe(2);
    expect(bagStep({ page: 'list', highlight: 3 }, { kind: 'down' }, ROWS).state.highlight).toBe(3);
  });

  it('opens Compare on a weapon not equipped, uses a usable item, and does nothing on the rest', () => {
    expect(bagStep(BAG_START, { kind: 'primary' }, ROWS)).toEqual({ state: { page: 'compare', highlight: 0, itemId: 'woodcutter_hatchet' } });
    expect(bagStep({ page: 'list', highlight: 1 }, { kind: 'primary' }, ROWS).action).toBeUndefined();
    expect(bagStep({ page: 'list', highlight: 2 }, { kind: 'primary' }, ROWS).action).toEqual({ kind: 'use', itemId: 'lantern_draught' });
    expect(bagStep({ page: 'list', highlight: 3 }, { kind: 'primary' }, ROWS).action).toBeUndefined();
  });

  it('equips from Compare, or goes back to the list with Back or Esc', () => {
    const compare = { page: 'compare', highlight: 0, itemId: 'woodcutter_hatchet' } as const;
    expect(bagStep(compare, { kind: 'primary' }, ROWS)).toEqual({
      state: { page: 'list', highlight: 0 }, action: { kind: 'equip', itemId: 'woodcutter_hatchet' },
    });
    const onBack = bagStep(compare, { kind: 'right' }, ROWS).state;
    expect(onBack.highlight).toBe(1);
    expect(bagStep(onBack, { kind: 'primary' }, ROWS)).toEqual({ state: { page: 'list', highlight: 0 } });
    expect(bagStep(compare, { kind: 'back' }, ROWS)).toEqual({ state: { page: 'list', highlight: 0 } });
    expect(bagStep(compare, { kind: 'pick', index: 0 }, ROWS).action).toEqual({ kind: 'equip', itemId: 'woodcutter_hatchet' });
  });

  it('closes the Bag with Esc on the list and switches tabs with left and right', () => {
    expect(bagStep(BAG_START, { kind: 'back' }, ROWS).action).toEqual({ kind: 'close' });
    expect(bagStep(BAG_START, { kind: 'right' }, ROWS).action).toEqual({ kind: 'tab', delta: 1 });
    expect(bagStep(BAG_START, { kind: 'left' }, ROWS).action).toEqual({ kind: 'tab', delta: -1 });
  });

  it('keeps the highlight inside a list that shrank', () => {
    expect(bagStep({ page: 'list', highlight: 9 }, { kind: 'up' }, ROWS).state.highlight).toBe(2);
  });
});

describe('compareRows', () => {
  const sword: WeaponView = { id: 's', name: 'Rusty Shortsword', tier: 'F', description: '', base: 20, attack: 20, critPct: 5, str: 'D', dex: 'D' };
  const bow: WeaponView = { id: 'b', name: "Hunter's Longbow", tier: 'D', description: '', base: 30, attack: 36, critPct: 10, dex: 'B' };

  it('sets the server numbers side by side and says which way each one goes', () => {
    expect(compareRows(sword, bow)).toEqual([
      { label: 'AR', before: '20', after: '36', change: 'better' },
      { label: 'Crit', before: '5%', after: '10%', change: 'better' },
      { label: 'Str', before: 'D', after: '–', change: 'worse' },
      { label: 'Dex', before: 'D', after: 'B', change: 'better' },
      { label: 'Arc', before: '–', after: '–', change: 'same' },
    ]);
  });

  it('compares against bare hands when nothing is equipped', () => {
    expect(compareRows(null, bow)[0]).toEqual({ label: 'AR', before: '–', after: '36', change: 'better' });
  });
});

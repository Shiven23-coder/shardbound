import { describe, expect, it } from 'vitest';
import type { MenuView } from '../api/types';
import { menuItems, menuStep, ROOT } from './battleMenu';

const menu: MenuView = {
  canFlee: true, fp: 30, maxFp: 30,
  skills: [
    { slot: 0, id: 'rending_cut', name: 'Rending Cut', element: 'PHYSICAL', fpCost: 5, usable: true },
    { slot: 1, id: 'crystal_pulse', name: 'Crystal Pulse', element: 'CRYSTAL', fpCost: 6, usable: false },
    { slot: 2, fpCost: 0, usable: false },
    { slot: 3, fpCost: 0, usable: false },
  ],
  bag: [{ id: 'healing_draught', name: 'Healing Draught', qty: 3, heal: 40, fp: 0 }],
};

describe('battle menu (P-57)', () => {
  it('has ATTACK, SKILLS, BAG and FLEE, and FLEE follows the server', () => {
    expect(menuItems('root', menu).map((i) => i.label)).toEqual(['Attack', 'Skills', 'Bag', 'Flee']);
    expect(menuItems('root', { ...menu, canFlee: false })[3]!.disabled).toBe(true);
    expect(menuItems('root', { ...menu, bag: [] })[2]!.disabled).toBe(true);
  });

  it('shows four skill slots with FP cost, an empty slot as a dash, and Back', () => {
    const skills = menuItems('skills', menu);
    expect(skills.map((i) => i.label)).toEqual(['Rending Cut', 'Crystal Pulse', '—', '—', 'Back']);
    expect(skills[0]!.detail).toBe('5 FP · Physical');
    expect(skills[1]!.disabled).toBe(true);
  });

  it('opens a page with E or a number, and takes an action from it', () => {
    let step = menuStep(ROOT, menu, { kind: 'primary' });
    expect(step.state.page).toBe('attack');
    step = menuStep(step.state, menu, { kind: 'pick', index: 1 });
    expect(step.action).toEqual({ kind: 'GUARD', slot: 0 });
    expect(step.state).toEqual(ROOT);
  });

  it('moves the highlight past disabled entries and goes back with Esc or B', () => {
    const skills = menuStep(ROOT, menu, { kind: 'pick', index: 1 }).state;
    expect(skills).toEqual({ page: 'skills', highlight: 0 });
    expect(menuStep(skills, menu, { kind: 'down' }).state.highlight).toBe(4);       // past the disabled slots to Back
    expect(menuStep(skills, menu, { kind: 'back' }).state).toEqual(ROOT);
    expect(menuStep(skills, menu, { kind: 'pick', index: 1 }).action).toBeUndefined();
  });

  it('uses an item from the bag', () => {
    const bag = menuStep(ROOT, menu, { kind: 'pick', index: 2 }).state;
    expect(menuStep(bag, menu, { kind: 'primary' }).action).toEqual({ kind: 'ITEM', slot: 0, itemId: 'healing_draught' });
  });
});

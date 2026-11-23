import { describe, expect, it } from 'vitest';
import { LOADOUT_START, loadoutStep, placed, rootStep, SLOTS } from './lanternMenu';

const skills = ['rending_cut', 'crystal_pulse', 'ember'];

describe('the Lantern menu (P-61)', () => {
  it('opens Level up, Skills or Leave by highlight, number or Esc', () => {
    expect(rootStep(0, { kind: 'primary' }).open).toBe('level');
    expect(rootStep(0, { kind: 'down' })).toEqual({ highlight: 1 });
    expect(rootStep(1, { kind: 'pick', index: 2 }).open).toBe('leave');
    expect(rootStep(0, { kind: 'back' }).open).toBe('leave');
    expect(rootStep(2, { kind: 'down' })).toEqual({ highlight: 2 });
  });
});

describe('the Skills page (P-61)', () => {
  it('chooses a slot, then the skill that goes in it', () => {
    let step = loadoutStep(LOADOUT_START, { kind: 'down' }, skills);
    step = loadoutStep(step.state, { kind: 'primary' }, skills);
    expect(step.state).toEqual({ slot: 1, highlight: 0 });
    step = loadoutStep(step.state, { kind: 'pick', index: 2 }, skills);
    expect(step.action).toEqual({ kind: 'set', slot: 1, skillId: 'ember' });
    expect(step.state).toEqual({ slot: null, highlight: 1 });
  });

  it('offers Empty after the skills, and Back after the slots', () => {
    const choosing = { slot: 0, highlight: skills.length };
    expect(loadoutStep(choosing, { kind: 'primary' }, skills).action).toEqual({ kind: 'set', slot: 0, skillId: null });
    expect(loadoutStep({ slot: null, highlight: SLOTS }, { kind: 'primary' }, skills).action).toEqual({ kind: 'back' });
  });

  it('Esc closes the chooser first, then the page', () => {
    expect(loadoutStep({ slot: 2, highlight: 0 }, { kind: 'back' }, skills).state).toEqual({ slot: null, highlight: 2 });
    expect(loadoutStep(LOADOUT_START, { kind: 'back' }, skills).action).toEqual({ kind: 'back' });
  });

  it('moves a skill that is already in another slot, so each skill is placed once', () => {
    expect(placed(['rending_cut', 'crystal_pulse', null, null], 2, 'rending_cut'))
      .toEqual([null, 'crystal_pulse', 'rending_cut', null]);
    expect(placed(['rending_cut', 'crystal_pulse', null, null], 0, null)).toEqual([null, 'crystal_pulse', null, null]);
  });
});

import { describe, expect, it } from 'vitest';
import type { AttributeId } from '../api/types';
import { ATTRIBUTES, BACK_ROW, CONFIRM_ROW, latestRequest, levelUpStep, pendingLevels, raisesOf, START } from './levelUp';

const values = Object.fromEntries(ATTRIBUTES.map((a) => [a.id, 10])) as Record<AttributeId, number>;

describe('the level-up screen (P-61)', () => {
  it('changes the pending points at once with − and +, never below zero or past the cap', () => {
    let state = levelUpStep(START, { kind: 'right' }, values, 99).state;
    state = levelUpStep(state, { kind: 'right' }, values, 99).state;
    expect(state.pending.VIGOR).toBe(2);
    state = levelUpStep(state, { kind: 'left' }, values, 99).state;
    state = levelUpStep(state, { kind: 'left' }, values, 99).state;
    state = levelUpStep(state, { kind: 'left' }, values, 99).state;
    expect(state.pending.VIGOR).toBe(0);
    const nearCap = levelUpStep(START, { kind: 'right' }, { ...values, VIGOR: 99 }, 99).state;
    expect(nearCap.pending.VIGOR).toBe(0);
  });

  it('moves between the attributes, Confirm and Back', () => {
    let state = START;
    for (let i = 0; i < 10; i++) state = levelUpStep(state, { kind: 'down' }, values, 99).state;
    expect(state.row).toBe(BACK_ROW);
    state = levelUpStep(state, { kind: 'up' }, values, 99).state;
    expect(state.row).toBe(CONFIRM_ROW);
  });

  it('confirms only with points pending, and goes back from Back or with Esc', () => {
    const onConfirm = { ...START, row: CONFIRM_ROW };
    expect(levelUpStep(onConfirm, { kind: 'primary' }, values, 99).action).toBeUndefined();
    const withPoints = { pending: { MIND: 1 }, row: CONFIRM_ROW };
    expect(levelUpStep(withPoints, { kind: 'primary' }, values, 99).action).toBe('confirm');
    expect(levelUpStep({ ...START, row: BACK_ROW }, { kind: 'primary' }, values, 99).action).toBe('back');
    expect(levelUpStep(START, { kind: 'back' }, values, 99).action).toBe('back');
  });

  it('E on an attribute adds a point', () => {
    expect(levelUpStep(START, { kind: 'primary' }, values, 99).state.pending.VIGOR).toBe(1);
  });

  it('sends only the attributes with points', () => {
    const state = { pending: { VIGOR: 2, MIND: 0, ARCANE: 1 }, row: 0 };
    expect(raisesOf(state)).toEqual({ VIGOR: 2, ARCANE: 1 });
    expect(pendingLevels(state)).toBe(3);
  });

  it('keeps only the newest preview: an older answer is ignored', () => {
    const requests = latestRequest();
    const first = requests.next();
    const second = requests.next();
    expect(requests.isLatest(first)).toBe(false);
    expect(requests.isLatest(second)).toBe(true);
  });
});

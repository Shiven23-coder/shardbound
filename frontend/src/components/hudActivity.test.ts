import { describe, expect, it } from 'vitest';
import { CALM_MS, changed, mayFade, wakes } from './hudActivity';
import { makeView } from '../testing/fixtures';

const hud = makeView().hud;

describe('compact HUD', () => {
  it('wakes on damage, FP use and level-up, not on healing', () => {
    expect(wakes(hud, { ...hud, hp: hud.hp - 3 })).toBe(true);
    expect(wakes(hud, { ...hud, fp: hud.fp - 1 })).toBe(true);
    expect(wakes(hud, { ...hud, level: hud.level + 1 })).toBe(true);
    expect(wakes({ ...hud, hp: 10 }, hud)).toBe(false);
    expect(wakes(null, hud)).toBe(false);
  });

  it('notices any change to the numbers it shows', () => {
    expect(changed(hud, { ...hud, hp: 10 })).toBe(true);
    expect(changed(hud, { ...hud, gold: 99 })).toBe(false);
  });

  it('fades only at full HP after a calm while with no enemy near', () => {
    expect(mayFade(hud, CALM_MS, false)).toBe(true);
    expect(mayFade(hud, CALM_MS - 1, false)).toBe(false);
    expect(mayFade({ ...hud, hp: hud.maxHp - 1 }, CALM_MS, false)).toBe(false);
    expect(mayFade(hud, CALM_MS, true)).toBe(false);
  });
});

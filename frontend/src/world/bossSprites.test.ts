import { describe, expect, it } from 'vitest';
import { bossKeysOnMap } from './bossSprites';

const fs = (globalThis as unknown as { process: { getBuiltinModule: (id: string) => unknown } }).process
  .getBuiltinModule('node:fs') as { readFileSync: (url: URL, encoding: 'utf8') => string };

// The boss strips load only with an area whose map names a boss (P-67), so a creep battle elsewhere never waits on them.
const map = (area: string) =>
  JSON.parse(fs.readFileSync(new URL(`../../../content/maps/${area}.tmj`, import.meta.url), 'utf8'));

describe('bossKeysOnMap', () => {
  it('finds the Herald on the Ash Rise, by its boss object and its trigger', () => {
    expect(bossKeysOnMap(map('eldmere_ash_rise'))).toEqual(['boss.ashen_herald']);
  });

  it('finds no boss in an area without one', () => {
    expect(bossKeysOnMap(map('eldmere_cottage_lane'))).toEqual([]);
    expect(bossKeysOnMap(map('eldmere_old_ruins'))).toEqual([]);
  });
});

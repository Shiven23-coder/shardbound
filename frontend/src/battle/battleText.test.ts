import { describe, expect, it } from 'vitest';
import { hpShown, readingAt, roundPages, TEXT_TIMING, textStep } from './battleText';

describe('battle text (P-57)', () => {
  it('shows each line as its own page, splitting only lines that do not fit, and keeps the line of each page', () => {
    const split = (line: string) => (line.length > 20 ? [line.slice(0, 20), line.slice(20)] : [line]);
    expect(roundPages(['You strike!', 'The Storm Rat takes 19 damage.'], split)).toEqual([
      { text: 'You strike!', line: 0 },
      { text: 'The Storm Rat takes ', line: 1 },
      { text: '19 damage.', line: 1 },
    ]);
  });

  it('gets faster from Slow to Instant', () => {
    expect(TEXT_TIMING.slow.pauseMs).toBeGreaterThan(TEXT_TIMING.normal.pauseMs);
    expect(TEXT_TIMING.fast.pauseMs).toBeLessThan(TEXT_TIMING.normal.pauseMs);
    expect(TEXT_TIMING.instant.charsPerTick).toBe(Infinity);
  });

  it('leaves time to read at the default speed: dialogue-speed typing, a second per line', () => {
    expect(TEXT_TIMING.fast.charsPerTick).toBeLessThanOrEqual(2);
    expect(TEXT_TIMING.fast.pauseMs).toBeGreaterThanOrEqual(1000);
  });

  it('skips the round on a fresh press and moves on page by page while held', () => {
    expect(textStep(false, false)).toEqual({ kind: 'skip' });
    expect(textStep(true, false)).toEqual({ kind: 'next' });
    expect(textStep(true, true)).toBeNull();
  });

  it('starts a new round on its first page even before any state update (the arena must not see the last round read)', () => {
    const lastRound = { key: '3:', page: 4, typed: true };
    expect(readingAt(lastRound, '4:')).toEqual({ key: '4:', page: 0, typed: false });
    expect(readingAt(lastRound, '3:')).toBe(lastRound);
  });

  it('keeps the HP bars in step with the text: damage shows once its line has', () => {
    const hits = [
      { onPlayer: false, amount: 17, crit: false, line: 0 },
      { onPlayer: true, amount: 9, crit: false, line: 2 },
      { onPlayer: true, amount: 4, crit: false, line: 4 },
    ];
    const you = { hp: 87, maxHp: 100 };
    expect(hpShown(you, true, hits, 0)).toBe(100);
    expect(hpShown(you, true, hits, 2)).toBe(91);
    expect(hpShown(you, true, hits, Infinity)).toBe(87);
    expect(hpShown({ hp: 41, maxHp: 58 }, false, hits, 0)).toBe(41);
  });
});

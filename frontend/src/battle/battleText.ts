// Battle text pacing (P-57): a round's lines show one after another, typed out
// at the player's Battle text speed and moved on after a short pause; one tap or
// E skips the rest of the round, and holding E moves on line by line at once.
import type { BattleTextSpeed } from '../settings/settings';

export interface TextTiming {
  /** Characters revealed per typewriter tick; Infinity shows each line at once. */
  charsPerTick: number;
  /** How long a finished line stays before the next one. */
  pauseMs: number;
}

/** Fast (the default) types at the dialogue's speed and leaves each line about a second. */
export const TEXT_TIMING: Record<BattleTextSpeed, TextTiming> = {
  slow: { charsPerTick: 0.5, pauseMs: 1900 },
  normal: { charsPerTick: 1, pauseMs: 1400 },
  fast: { charsPerTick: 2, pauseMs: 1000 },
  instant: { charsPerTick: Infinity, pauseMs: 750 },
};

/** A page of the round's text and the index of the line it comes from (the arena plays that line's hits). */
export interface RoundPage {
  text: string;
  line: number;
}

/** The round's pages: each line is a page, broken further only if it does not fit (the dialogue pager). */
export function roundPages(lines: string[], split: (line: string) => string[]): RoundPage[] {
  return lines.flatMap((line, index) => split(line).map((text) => ({ text, line: index })));
}

/** Where the reading of a round is: its page, and whether that page has finished typing. */
export interface Reading {
  /** The round it belongs to (round and outcome). */
  key: string;
  page: number;
  typed: boolean;
}

/**
 * The reading of the round `key`: a reading left from an earlier round counts
 * as the new round's first page, so nothing sees the old round's "all read"
 * between a new view and the state update that follows it.
 */
export function readingAt(reading: Reading, key: string): Reading {
  return reading.key === key ? reading : { key, page: 0, typed: false };
}

/**
 * The HP an info box shows while a round's text plays: the fighter's HP after
 * the round, plus the damage of hits whose line has not shown yet, so a bar
 * drains when its hit plays in the arena.
 */
export function hpShown(
  fighter: { hp: number; maxHp: number },
  onPlayer: boolean,
  hits: { onPlayer: boolean; amount: number; line: number }[],
  shownLine: number,
): number {
  const pending = hits.filter((hit) => hit.onPlayer === onPlayer && hit.line > shownLine)
    .reduce((sum, hit) => sum + hit.amount, 0);
  return Math.min(fighter.maxHp, fighter.hp + pending);
}

export type TextStep = { kind: 'next' } | { kind: 'skip' } | null;

/**
 * What the primary input (tap, E, A) does while text is showing: a fresh press
 * skips the rest of the round; a held key's repeats move on one page each.
 */
export function textStep(held: boolean, lastPage: boolean): TextStep {
  if (held) return lastPage ? null : { kind: 'next' };
  return { kind: 'skip' };
}

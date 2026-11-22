// Getting back into the current run after a reload or a failed request (P-30).
// Only the server's definite "this run does not exist" ends the session;
// anything else (the API restarting, a network drop, a 5xx, a conflict) is
// waited out with growing pauses until the run comes back.
import { ApiError } from './client';
import type { SessionView } from './types';

export const RUN_NOT_FOUND = 'RUN_NOT_FOUND';

const FIRST_DELAY_MS = 500;
const MAX_DELAY_MS = 8_000;
const JITTER = 0.2;

export type ResumeOutcome = { kind: 'resumed'; view: SessionView } | { kind: 'gone' } | { kind: 'cancelled' };

export interface ResumeOptions {
  /** Called before each wait, with the 1-based attempt that just failed. */
  onRetry?: (attempt: number) => void;
  /** Stops the loop between attempts (e.g. the player went back to the title). */
  signal?: AbortSignal;
  sleep?: (ms: number) => Promise<void>;
  random?: () => number;
}

/** True only for the server's definite answer that the run no longer exists. */
export function isRunGone(err: unknown): boolean {
  return err instanceof ApiError && err.code === RUN_NOT_FOUND;
}

/** Pause before retry number `attempt` (1-based): 0.5, 1, 2, 4, 8, 8 … s, each ±20 %. */
export function backoffDelay(attempt: number, random: () => number = Math.random): number {
  const base = Math.min(MAX_DELAY_MS, FIRST_DELAY_MS * 2 ** (attempt - 1));
  return Math.round(base * (1 - JITTER + 2 * JITTER * random()));
}

const realSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Fetches the run until it answers, the server says it is gone, or the caller cancels. */
export async function resumeRun(fetchView: () => Promise<SessionView>, options: ResumeOptions = {}): Promise<ResumeOutcome> {
  const { onRetry, signal, sleep = realSleep, random = Math.random } = options;
  for (let attempt = 1; ; attempt++) {
    if (signal?.aborted) return { kind: 'cancelled' };
    try {
      return { kind: 'resumed', view: await fetchView() };
    } catch (err) {
      if (isRunGone(err)) return { kind: 'gone' };
      onRetry?.(attempt);
      await sleep(backoffDelay(attempt, random));
    }
  }
}

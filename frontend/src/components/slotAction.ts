import { errorMessage } from '../api/client';

/** What a save, load or delete led to. Success is explicit: a 204 has no value to test. */
export type SlotOutcome<T> = { ok: true; value: T } | { ok: false; message: string };

// Runs one slot action and turns its result or error into an outcome.
export async function runSlotAction<T>(action: () => Promise<T>): Promise<SlotOutcome<T>> {
  try {
    return { ok: true, value: await action() };
  } catch (err) {
    return { ok: false, message: errorMessage(err) };
  }
}

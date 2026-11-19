import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { runSlotAction } from './slotAction';

describe('runSlotAction', () => {
  it('counts an action that resolves with nothing (HTTP 204) as success', async () => {
    const outcome = await runSlotAction(async () => undefined);
    expect(outcome).toEqual({ ok: true, value: undefined });
  });

  it('passes the value through on success', async () => {
    const outcome = await runSlotAction(async () => 42);
    expect(outcome).toEqual({ ok: true, value: 42 });
  });

  it("reports the server's message on failure", async () => {
    const outcome = await runSlotAction(async () => {
      throw new ApiError(409, 'ACTION_NOT_ALLOWED', 'You cannot save during combat.');
    });
    expect(outcome).toEqual({ ok: false, message: 'You cannot save during combat.' });
  });
});

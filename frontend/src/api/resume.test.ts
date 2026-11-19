import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { backoffDelay, resumeRun } from './resume';
import { makeView } from '../testing/fixtures';

const noSleep = () => Promise.resolve();

describe('backoffDelay', () => {
  it('doubles from half a second up to eight seconds', () => {
    const middle = () => 0.5; // no jitter
    expect([1, 2, 3, 4, 5, 6, 9].map((n) => backoffDelay(n, middle))).toEqual([500, 1000, 2000, 4000, 8000, 8000, 8000]);
  });

  it('jitters by at most 20 % either way', () => {
    expect(backoffDelay(1, () => 0)).toBe(400);
    expect(backoffDelay(1, () => 1)).toBe(600);
  });
});

describe('resumeRun', () => {
  it('returns the run at once when the server answers', async () => {
    const view = makeView();
    await expect(resumeRun(async () => view, { sleep: noSleep })).resolves.toEqual({ kind: 'resumed', view });
  });

  it('keeps retrying through network errors, 5xx and conflicts until the run comes back', async () => {
    const view = makeView();
    const fetchView = vi.fn<() => Promise<typeof view>>()
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK', 'down'))
      .mockRejectedValueOnce(new ApiError(502, 'HTTP_502', 'bad gateway'))
      .mockRejectedValueOnce(new ApiError(409, 'RUN_CONFLICT', 'moved on'))
      .mockResolvedValueOnce(view);
    const onRetry = vi.fn();

    await expect(resumeRun(fetchView, { sleep: noSleep, onRetry })).resolves.toEqual({ kind: 'resumed', view });
    expect(onRetry.mock.calls.map(([attempt]) => attempt)).toEqual([1, 2, 3]);
  });

  it('gives up only when the server says the run is gone', async () => {
    const fetchView = vi.fn().mockRejectedValue(new ApiError(404, 'RUN_NOT_FOUND', 'no such run'));
    await expect(resumeRun(fetchView, { sleep: noSleep })).resolves.toEqual({ kind: 'gone' });
    expect(fetchView).toHaveBeenCalledTimes(1);
  });

  it('a different 404 is not "gone"', async () => {
    const view = makeView();
    const fetchView = vi.fn()
      .mockRejectedValueOnce(new ApiError(404, 'HTTP_404', 'proxy without a route'))
      .mockResolvedValueOnce(view);
    await expect(resumeRun(fetchView, { sleep: noSleep })).resolves.toEqual({ kind: 'resumed', view });
  });

  it('stops when cancelled', async () => {
    const controller = new AbortController();
    const fetchView = vi.fn().mockRejectedValue(new ApiError(0, 'NETWORK', 'down'));
    const sleep = async () => controller.abort();
    await expect(resumeRun(fetchView, { sleep, signal: controller.signal })).resolves.toEqual({ kind: 'cancelled' });
    expect(fetchView).toHaveBeenCalledTimes(1);
  });
});

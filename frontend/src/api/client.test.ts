import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, getSessionId, setSessionId } from './client';
import { MemoryStorage, blockedStorage, makeView } from '../testing/fixtures';

const fetchMock = vi.fn<(request: Request) => Promise<Response>>();
let storage: MemoryStorage;

// A fresh Response each time (a body can only be read once).
function jsonResponse(body: unknown, status = 200, type = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } });
}

// The n-th request the client sent (0-based), with its path and body read out.
async function sentRequest(n: number) {
  const call = fetchMock.mock.calls[n];
  if (!call) throw new Error(`fetch was not called ${n + 1} time(s)`);
  const request = call[0];
  const url = new URL(request.url);
  return {
    path: url.pathname + url.search,
    method: request.method,
    headers: request.headers,
    body: await request.clone().text(),
  };
}

// Awaits a promise that should fail and returns the ApiError it failed with.
async function apiErrorFrom(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (err) {
    if (err instanceof ApiError) return err;
    throw err;
  }
  throw new Error('expected the request to fail');
}

beforeEach(() => {
  storage = new MemoryStorage();
  vi.stubGlobal('sessionStorage', storage);
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockReset();
  setSessionId(null);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('session header', () => {
  it('sends no X-Session until a SessionView with a sessionId arrives, then sends it', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(makeView({ sessionId: 'abc-123' })));
    fetchMock.mockResolvedValueOnce(jsonResponse(makeView({ sessionId: 'abc-123' })));

    await api.newGame(7, false);
    await api.choose(1);

    expect((await sentRequest(0)).headers.get('X-Session')).toBeNull();
    expect((await sentRequest(1)).headers.get('X-Session')).toBe('abc-123');
    expect(getSessionId()).toBe('abc-123');
    expect(storage.getItem('shardbound.sessionId')).toBe('abc-123');
  });

  it('keeps the session in memory when sessionStorage is blocked', () => {
    vi.stubGlobal('sessionStorage', blockedStorage);
    expect(() => setSessionId('xyz')).not.toThrow();
    expect(getSessionId()).toBe('xyz');
  });
});

describe('requests', () => {
  it('POSTs JSON bodies with a Content-Type header', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(makeView()));
    await api.combatAction('ITEM', 1, 'potion_small');

    const sent = await sentRequest(0);
    expect(sent.path).toBe('/api/combat/action');
    expect(sent.method).toBe('POST');
    expect(sent.headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(sent.body)).toEqual({ type: 'ITEM', target: 1, itemId: 'potion_small' });
  });

  it('puts playerId in the query string for saves, achievements and endings', async () => {
    fetchMock.mockImplementation(async () => jsonResponse([]));
    await api.listSaves(7);
    await api.achievements(7);
    await api.endings(7);

    expect((await sentRequest(0)).path).toBe('/api/saves?playerId=7');
    expect((await sentRequest(1)).path).toBe('/api/achievements?playerId=7');
    expect((await sentRequest(2)).path).toBe('/api/endings?playerId=7');
  });

  it('fills the slot into the path for load and delete', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(makeView()));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api.load(2, 7);
    await api.deleteSave(3, 7);

    expect(await sentRequest(0)).toMatchObject({ path: '/api/saves/2/load?playerId=7', method: 'POST' });
    expect(await sentRequest(1)).toMatchObject({ path: '/api/saves/3?playerId=7', method: 'DELETE' });
  });

  it('sends world events with the player position and dialogue choices to their endpoints', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(makeView()));
    await api.worldExit('to_ruins', 312, 8, 'UP');
    await api.worldInteract('maren', 120, 120, 'DOWN');
    await api.choose(4);

    const exit = await sentRequest(0);
    expect(exit.path).toBe('/api/world/exit');
    expect(JSON.parse(exit.body)).toEqual({ exitId: 'to_ruins', x: 312, y: 8, facing: 'UP' });
    const interact = await sentRequest(1);
    expect(interact.path).toBe('/api/world/interact');
    expect(JSON.parse(interact.body)).toEqual({ targetId: 'maren', x: 120, y: 120, facing: 'DOWN' });
    expect((await sentRequest(2)).path).toBe('/api/dialogue/choose');
  });

  it('treats 204 No Content as success', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.save(1)).resolves.toBeUndefined();
  });
});

describe('errors', () => {
  it('turns a problem+json body into an ApiError with its code and detail', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(
      { status: 409, code: 'ACTION_NOT_ALLOWED', title: 'Conflict', detail: 'You cannot save during combat.' },
      409,
      'application/problem+json',
    ));
    const err = await apiErrorFrom(api.save(1));

    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(409);
    expect(err.code).toBe('ACTION_NOT_ALLOWED');
    expect(err.message).toBe('You cannot save during combat.');
  });

  it('keeps the last validated position of a rejected world event', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(
      {
        status: 409, code: 'WORLD_EVENT_REJECTED', title: 'Conflict', detail: 'You could not have walked that far so quickly.',
        lastPosition: { areaId: 'eldmere_cottage_lane', x: 120, y: 168, facing: 'DOWN' },
      },
      409,
      'application/problem+json',
    ));
    const err = await apiErrorFrom(api.worldExit('to_ruins', 312, 8, 'UP'));

    expect(err.code).toBe('WORLD_EVENT_REJECTED');
    expect(err.lastPosition).toEqual({ areaId: 'eldmere_cottage_lane', x: 120, y: 168, facing: 'DOWN' });
  });

  it('leaves out a malformed last position', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(
      { status: 409, code: 'WORLD_EVENT_REJECTED', detail: 'No.', lastPosition: { x: 'far' } },
      409,
      'application/problem+json',
    ));
    const err = await apiErrorFrom(api.worldExit('to_ruins', 312, 8, 'UP'));
    expect(err.lastPosition).toBeUndefined();
  });

  it('still gives an ApiError when the error body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad gateway</html>', { status: 502 }));
    const err = await apiErrorFrom(api.getGame());

    expect(err.status).toBe(502);
    expect(err.code).toBe('HTTP_502');
    expect(err.message).toBe('Request failed (502)');
  });

  it('reports a network failure as status 0 / NETWORK', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('fetch failed'));
    const err = await apiErrorFrom(api.leaderboard());

    expect(err.status).toBe(0);
    expect(err.code).toBe('NETWORK');
  });
});

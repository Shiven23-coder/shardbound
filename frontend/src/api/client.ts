// Typed client for the Java backend, generated from api/openapi.json.
// No game rules live here: every function sends a player intent and returns
// what the server said.
import createClient from 'openapi-fetch';
import type { paths } from './schema.gen';
import type {
  AchievementView,
  CombatActionType,
  EndingInfoView,
  GameView,
  InspectorView,
  LeaderboardView,
  Problem,
  ProfileResponse,
  SlotView,
} from './types';

const SESSION_KEY = 'shardbound.sessionId';
const SESSION_HEADER = 'X-Session';

// An error from the backend: the problem's `code` and `detail` plus the HTTP status.
// status is 0 when the server could not be reached at all.
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

// Text to show for any thrown value (an ApiError, another Error, or anything else).
export function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

// The session id is kept in memory and mirrored to sessionStorage so a
// page refresh can pick the same game back up. Storage can be blocked
// (private mode, strict settings), so every access is wrapped in try/catch.
let sessionId: string | null = readStoredSession();

function readStoredSession(): string | null {
  try {
    return sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function getSessionId(): string | null {
  return sessionId;
}

export function setSessionId(id: string | null): void {
  sessionId = id || null;
  try {
    if (sessionId) sessionStorage.setItem(SESSION_KEY, sessionId);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // Storage unavailable: the in-memory copy is still used.
  }
}

// Same-origin in the browser (Vite proxies /api in dev; the CDN does in production).
const baseUrl = typeof location === 'undefined' ? 'http://localhost' : location.origin;

const client = createClient<paths>({
  baseUrl,
  // Looked up on every call so tests can replace fetch after this module loads.
  fetch: (request) => globalThis.fetch(request),
});

client.use({
  onRequest({ request }) {
    if (sessionId) request.headers.set(SESSION_HEADER, sessionId);
    return request;
  },
});

function isProblem(value: unknown): value is Problem {
  return typeof value === 'object' && value !== null && typeof (value as Problem).code === 'string';
}

type Result<T> = { data?: T; error?: unknown; response: Response };

// Awaits an openapi-fetch call and returns its data, or throws an ApiError.
async function call<T>(send: () => Promise<Result<T>>): Promise<T> {
  let result: Result<T>;
  try {
    result = await send();
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Is the backend running?');
  }
  const { response } = result;
  if (!response.ok) {
    const problem = isProblem(result.error) ? result.error : undefined;
    throw new ApiError(
      response.status,
      problem?.code ?? `HTTP_${response.status}`,
      problem?.detail ?? `Request failed (${response.status})`,
    );
  }
  return result.data as T;
}

// Any GameView carries the session id; remember it for later requests.
async function game(send: () => Promise<Result<GameView>>): Promise<GameView> {
  const view = await call(send);
  setSessionId(view.sessionId);
  return view;
}

const byPlayer = (playerId: number) => ({ params: { query: { playerId } } });

export const api = {
  createProfile: (username: string): Promise<ProfileResponse> =>
    call(() => client.POST('/api/profiles', { body: { username } })),
  newGame: (playerId: number, demoMode: boolean): Promise<GameView> =>
    game(() => client.POST('/api/game/new', { body: { playerId, demoMode } })),
  getGame: (): Promise<GameView> => game(() => client.GET('/api/game')),
  choose: (choiceId: number): Promise<GameView> =>
    game(() => client.POST('/api/game/choose', { body: { choiceId } })),
  combatAction: (type: CombatActionType, target: number, itemId?: string): Promise<GameView> =>
    game(() => client.POST('/api/combat/action', { body: { type, target, itemId } })),
  equip: (itemId: string): Promise<GameView> =>
    game(() => client.POST('/api/inventory/equip', { body: { itemId } })),
  useItem: (itemId: string): Promise<GameView> =>
    game(() => client.POST('/api/inventory/use', { body: { itemId } })),
  listSaves: (playerId: number): Promise<SlotView[]> =>
    call(() => client.GET('/api/saves', byPlayer(playerId))),
  save: (slot: number): Promise<void> =>
    call(() => client.POST('/api/saves/{slot}', { params: { path: { slot } } })),
  load: (slot: number, playerId: number): Promise<GameView> =>
    game(() => client.POST('/api/saves/{slot}/load', { params: { path: { slot }, query: { playerId } } })),
  deleteSave: (slot: number, playerId: number): Promise<void> =>
    call(() => client.DELETE('/api/saves/{slot}', { params: { path: { slot }, query: { playerId } } })),
  achievements: (playerId: number): Promise<AchievementView[]> =>
    call(() => client.GET('/api/achievements', byPlayer(playerId))),
  endings: (playerId: number): Promise<EndingInfoView[]> =>
    call(() => client.GET('/api/endings', byPlayer(playerId))),
  leaderboard: (): Promise<LeaderboardView[]> => call(() => client.GET('/api/leaderboard')),
  inspector: (): Promise<InspectorView> => call(() => client.GET('/api/debug/inspector')),
};

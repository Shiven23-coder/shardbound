// Thin fetch wrapper for the Java backend. No game rules live here:
// every function sends a player intent and returns what the server said.

const SESSION_KEY = 'shardbound.sessionId';

// The session id is kept in memory and mirrored to sessionStorage so a
// page refresh can pick the same game back up. Storage can be blocked
// (private mode, strict settings), so every access is wrapped in try/catch.
let sessionId = readStoredSession();

function readStoredSession() {
  try {
    return sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function getSessionId() {
  return sessionId;
}

export function setSessionId(id) {
  sessionId = id || null;
  try {
    if (sessionId) sessionStorage.setItem(SESSION_KEY, sessionId);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // Storage unavailable: the in-memory copy is still used.
  }
}

// Builds an Error carrying the server's {error, message} plus the HTTP status.
function apiError(status, code, message) {
  const err = new Error(message);
  err.status = status;
  err.error = code;
  return err;
}

// Reads the body as JSON, tolerating empty or non-JSON bodies.
async function readBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function request(method, path, body) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (sessionId) headers['X-Session'] = sessionId;

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw apiError(0, 'NETWORK', 'Cannot reach the server. Is the backend running?');
  }

  const data = await readBody(response);
  if (!response.ok) {
    const code = data?.error ?? `HTTP_${response.status}`;
    const message = data?.message ?? `Request failed (${response.status})`;
    throw apiError(response.status, code, message);
  }
  // Any GameView carries the session id; remember it for later requests.
  if (data && typeof data.sessionId === 'string') setSessionId(data.sessionId);
  return data;
}

const pid = (playerId) => `playerId=${encodeURIComponent(playerId)}`;

export const api = {
  createProfile: (username) => request('POST', '/api/profiles', { username }),
  newGame: (playerId, demoMode) => request('POST', '/api/game/new', { playerId, demoMode }),
  getGame: () => request('GET', '/api/game'),
  choose: (choiceId) => request('POST', '/api/game/choose', { choiceId }),
  combatAction: (type, target, itemId = null) =>
    request('POST', '/api/combat/action', { type, target, itemId }),
  equip: (itemId) => request('POST', '/api/inventory/equip', { itemId }),
  useItem: (itemId) => request('POST', '/api/inventory/use', { itemId }),
  listSaves: (playerId) => request('GET', `/api/saves?${pid(playerId)}`),
  save: (slot) => request('POST', `/api/saves/${slot}`),
  load: (slot, playerId) => request('POST', `/api/saves/${slot}/load?${pid(playerId)}`),
  deleteSave: (slot, playerId) => request('DELETE', `/api/saves/${slot}?${pid(playerId)}`),
  achievements: (playerId) => request('GET', `/api/achievements?${pid(playerId)}`),
  endings: (playerId) => request('GET', `/api/endings?${pid(playerId)}`),
  leaderboard: () => request('GET', '/api/leaderboard'),
  inspector: () => request('GET', '/api/debug/inspector'),
};

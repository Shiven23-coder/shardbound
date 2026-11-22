// UI state and the reducer that changes it. Kept free of React so it can be
// unit-tested on its own. The SessionView from the server is stored verbatim in
// `view`; the reducer never edits it (no game rules here).
import type { SessionView } from '../api/types';

const PLAYER_KEY = 'shardbound.player';

export type Screen = 'title' | 'profile' | 'game' | 'achievements' | 'leaderboard';

/** What happens after a profile is picked on the profile screen. */
export type ProfileNext = 'new' | 'load' | 'achievements';

/** Which tab the SaveLoadModal shows. */
export type ModalTab = 'save' | 'load';

export interface Player {
  playerId: number;
  username: string;
}

/** seq grows with every error so the same message can be shown twice. */
export interface ErrorState {
  message: string;
  seq: number;
}

export interface State {
  screen: Screen;
  returnScreen: Screen;      // where "Back" goes from achievements / leaderboard
  profileNext: ProfileNext;
  player: Player | null;
  view: SessionView | null;     // latest SessionView, exactly as sent by the server
  viewSeq: number;           // bumps on every new SessionView (used as animation keys)
  error: ErrorState | null;
  busy: boolean;
  inspectorOpen: boolean;
  demoMode: boolean;
  modal: ModalTab | null;    // null = closed
  reconnecting: boolean;     // the server is unreachable and the run is being waited for (P-30)
}

export type Action =
  | { type: 'GO'; screen: Screen; returnTo?: Screen }
  | { type: 'GO_PROFILE'; next: ProfileNext }
  | { type: 'SET_PLAYER'; player: Player | null }
  | { type: 'SET_VIEW'; view: SessionView }
  | { type: 'QUIT' }
  | { type: 'SET_ERROR'; message: string }
  | { type: 'SET_BUSY'; busy: boolean }
  | { type: 'TOGGLE_INSPECTOR' }
  | { type: 'SET_DEMO'; demoMode: boolean }
  | { type: 'OPEN_MODAL'; tab: ModalTab }
  | { type: 'CLOSE_MODAL' }
  | { type: 'SET_RECONNECTING'; reconnecting: boolean };

// True when a value read from storage looks like a Player.
function isPlayer(value: unknown): value is Player {
  if (typeof value !== 'object' || value === null) return false;
  return 'playerId' in value && typeof value.playerId === 'number'
    && 'username' in value && typeof value.username === 'string';
}

// The profile chosen earlier in this browser tab, if any.
export function readStoredPlayer(): Player | null {
  try {
    const raw = sessionStorage.getItem(PLAYER_KEY);
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    return isPlayer(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

// Remembers the chosen profile for this browser tab.
export function storePlayer(player: Player | null): void {
  try {
    sessionStorage.setItem(PLAYER_KEY, JSON.stringify(player));
  } catch {
    // Storage unavailable: nothing to do.
  }
}

export const initialState: State = {
  screen: 'title',
  returnScreen: 'title',
  profileNext: 'new',
  player: readStoredPlayer(),
  view: null,
  viewSeq: 0,
  error: null,
  busy: false,
  inspectorOpen: false,
  demoMode: false,
  modal: null,
  reconnecting: false,
};

// Pure function: returns the next state for one action. Time O(1).
export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'GO':
      return { ...state, screen: action.screen, returnScreen: action.returnTo ?? state.screen };
    case 'GO_PROFILE':
      return { ...state, screen: 'profile', profileNext: action.next };
    case 'SET_PLAYER':
      return { ...state, player: action.player };
    case 'SET_VIEW':
      return { ...state, view: action.view, viewSeq: state.viewSeq + 1, screen: 'game' };
    case 'QUIT':
      return { ...state, view: null, screen: 'title', modal: null };
    case 'SET_ERROR':
      return { ...state, error: { message: action.message, seq: (state.error?.seq ?? 0) + 1 } };
    case 'SET_BUSY':
      return { ...state, busy: action.busy };
    case 'TOGGLE_INSPECTOR':
      return { ...state, inspectorOpen: !state.inspectorOpen };
    case 'SET_DEMO':
      return { ...state, demoMode: action.demoMode };
    case 'OPEN_MODAL':
      return { ...state, modal: action.tab };
    case 'CLOSE_MODAL':
      return { ...state, modal: null };
    case 'SET_RECONNECTING':
      return { ...state, reconnecting: action.reconnecting };
    default:
      return state;
  }
}

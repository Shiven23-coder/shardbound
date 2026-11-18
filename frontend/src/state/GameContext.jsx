import { createContext, useCallback, useContext, useEffect, useMemo, useReducer } from 'react';
import { api, getSessionId, setSessionId } from '../api/client.js';

// Global UI state. The GameView from the server is stored verbatim in `view`;
// React never edits it, it only renders it.
const PLAYER_KEY = 'shardbound.player';

function readStoredPlayer() {
  try {
    return JSON.parse(sessionStorage.getItem(PLAYER_KEY));
  } catch {
    return null;
  }
}

const initialState = {
  screen: 'title',        // title | profile | game | achievements | leaderboard
  returnScreen: 'title',  // where "Back" goes from achievements / leaderboard
  profileNext: 'new',     // what happens after a profile is picked: new | load | achievements
  player: readStoredPlayer(), // { playerId, username }
  view: null,             // latest GameView, exactly as sent by the server
  viewSeq: 0,             // bumps on every new GameView (used as animation keys)
  error: null,            // { message, seq }
  busy: false,
  inspectorOpen: false,
  demoMode: false,
  modal: null,            // null | 'save' | 'load'  (SaveLoadModal tab)
};

function reducer(state, action) {
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
    default:
      return state;
  }
}

const GameContext = createContext(null);

export function GameProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // Remember the chosen profile for this browser tab.
  useEffect(() => {
    try {
      sessionStorage.setItem(PLAYER_KEY, JSON.stringify(state.player));
    } catch {
      // Storage unavailable: nothing to do.
    }
  }, [state.player]);

  // Runs an API call that returns a GameView, stores the result, and turns
  // failures into an error toast. Returns true on success.
  const run = useCallback(async (apiCall) => {
    dispatch({ type: 'SET_BUSY', busy: true });
    try {
      const view = await apiCall();
      dispatch({ type: 'SET_VIEW', view });
      return true;
    } catch (err) {
      dispatch({ type: 'SET_ERROR', message: err.message });
      return false;
    } finally {
      dispatch({ type: 'SET_BUSY', busy: false });
    }
  }, []);

  // Leaves the current game: forget the session and go back to the title.
  const quit = useCallback(() => {
    setSessionId(null);
    dispatch({ type: 'QUIT' });
  }, []);

  // After a page refresh, resume the game this tab was playing (if any).
  useEffect(() => {
    if (!getSessionId() || !initialState.player) return;
    api.getGame()
      .then((view) => dispatch({ type: 'SET_VIEW', view }))
      .catch(() => {}); // stale session: just stay on the title screen
  }, []);

  const value = useMemo(() => ({ state, dispatch, run, quit }), [state, run, quit]);
  return <GameContext.Provider value={value}>{children}</GameContext.Provider>;
}

export function useGame() {
  return useContext(GameContext);
}

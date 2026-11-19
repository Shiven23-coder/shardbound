import { createContext, useCallback, useContext, useEffect, useMemo, useReducer } from 'react';
import type { Dispatch, ReactNode } from 'react';
import { ApiError, api, errorMessage, getSessionId, setSessionId } from '../api/client';
import type { GameView } from '../api/types';
import { initialState, reducer, storePlayer } from './gameReducer';
import type { Action, State } from './gameReducer';

// Global UI state shared by every screen. The reducer and state types live in
// gameReducer.ts; this file only wires them into React.

export interface GameContextValue {
  state: State;
  dispatch: Dispatch<Action>;
  /** Runs an API call that returns a GameView; resolves to true on success. */
  run: (apiCall: () => Promise<GameView>) => Promise<boolean>;
  quit: () => void;
}

const GameContext = createContext<GameContextValue | null>(null);

interface GameProviderProps {
  children: ReactNode;
}

export function GameProvider({ children }: GameProviderProps) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // Remember the chosen profile for this browser tab.
  useEffect(() => {
    storePlayer(state.player);
  }, [state.player]);

  // Runs an API call that returns a GameView, stores the result, and turns
  // failures into an error toast. Returns true on success.
  const run = useCallback(async (apiCall: () => Promise<GameView>) => {
    dispatch({ type: 'SET_BUSY', busy: true });
    try {
      const view = await apiCall();
      dispatch({ type: 'SET_VIEW', view });
      return true;
    } catch (err) {
      dispatch({ type: 'SET_ERROR', message: errorMessage(err) });
      // The game moved on elsewhere (another tab or server): show the latest state.
      if (err instanceof ApiError && err.code === 'RUN_CONFLICT') {
        await api.getGame().then((view) => dispatch({ type: 'SET_VIEW', view })).catch(() => {});
      }
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

export function useGame(): GameContextValue {
  const value = useContext(GameContext);
  if (value === null) throw new Error('useGame must be used inside <GameProvider>');
  return value;
}

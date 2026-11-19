import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useRef } from 'react';
import type { Dispatch, ReactNode } from 'react';
import { ApiError, api, errorMessage, getSessionId, setSessionId } from '../api/client';
import { isRunGone, resumeRun } from '../api/resume';
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
  /** Gets back into the current run, waiting out outages (P-30). */
  resume: () => void;
  /** Stops waiting for the server and shows the title; the session is kept. */
  leaveToTitle: () => void;
  quit: () => void;
}

const GameContext = createContext<GameContextValue | null>(null);

const RUN_ENDED_MESSAGE = 'That game is no longer available. Start a new one or load a save.';

// Failures that say nothing about the run itself: the API is restarting or unreachable.
function isTransient(err: unknown): boolean {
  if (!(err instanceof ApiError)) return true;
  return err.status === 0 || err.status >= 500 || err.code === 'RUN_CONFLICT';
}

interface GameProviderProps {
  children: ReactNode;
}

export function GameProvider({ children }: GameProviderProps) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const reconnecting = useRef<AbortController | null>(null);

  // Remember the chosen profile for this browser tab.
  useEffect(() => {
    storePlayer(state.player);
  }, [state.player]);

  // The server said the run does not exist: only now is the session dropped.
  const runGone = useCallback(() => {
    setSessionId(null);
    dispatch({ type: 'QUIT' });
    dispatch({ type: 'SET_ERROR', message: RUN_ENDED_MESSAGE });
  }, []);

  const resume = useCallback(() => {
    if (!getSessionId() || reconnecting.current) return;
    const controller = new AbortController();
    reconnecting.current = controller;
    resumeRun(api.getGame, {
      signal: controller.signal,
      onRetry: () => dispatch({ type: 'SET_RECONNECTING', reconnecting: true }),
    }).then((outcome) => {
      if (controller.signal.aborted) return; // the player left, or React remounted
      if (outcome.kind === 'resumed') dispatch({ type: 'SET_VIEW', view: outcome.view });
      if (outcome.kind === 'gone') runGone();
    }).finally(() => {
      if (reconnecting.current === controller) reconnecting.current = null;
      dispatch({ type: 'SET_RECONNECTING', reconnecting: false });
    });
  }, [runGone]);

  const leaveToTitle = useCallback(() => {
    reconnecting.current?.abort();
    reconnecting.current = null;
    dispatch({ type: 'SET_RECONNECTING', reconnecting: false });
    dispatch({ type: 'GO', screen: 'title' });
  }, []);

  // Runs an API call that returns a GameView and stores the result. A rule
  // the server refused becomes an error toast; an outage starts reconnecting.
  const run = useCallback(async (apiCall: () => Promise<GameView>) => {
    dispatch({ type: 'SET_BUSY', busy: true });
    try {
      const view = await apiCall();
      dispatch({ type: 'SET_VIEW', view });
      return true;
    } catch (err) {
      if (isRunGone(err)) {
        runGone();
      } else if (isTransient(err)) {
        resume(); // the action may or may not have happened: the server's view decides (idempotency keys in V4)
      } else {
        dispatch({ type: 'SET_ERROR', message: errorMessage(err) });
      }
      return false;
    } finally {
      dispatch({ type: 'SET_BUSY', busy: false });
    }
  }, [resume, runGone]);

  // Leaves the current game: forget the session and go back to the title.
  const quit = useCallback(() => {
    reconnecting.current?.abort();
    setSessionId(null);
    dispatch({ type: 'QUIT' });
  }, []);

  // After a page load, get back into the game this tab was playing (if any).
  useEffect(() => {
    if (initialState.player) resume();
    return () => {
      // React StrictMode mounts twice in development: stop this loop so the next mount starts its own.
      reconnecting.current?.abort();
      reconnecting.current = null;
    };
  }, [resume]);

  const value = useMemo(
    () => ({ state, dispatch, run, resume, leaveToTitle, quit }),
    [state, run, resume, leaveToTitle, quit],
  );
  return <GameContext.Provider value={value}>{children}</GameContext.Provider>;
}

export function useGame(): GameContextValue {
  const value = useContext(GameContext);
  if (value === null) throw new Error('useGame must be used inside <GameProvider>');
  return value;
}

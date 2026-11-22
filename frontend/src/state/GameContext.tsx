import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useRef } from 'react';
import type { Dispatch, ReactNode } from 'react';
import { ApiError, api, errorMessage, getSessionId, setSessionId } from '../api/client';
import { isRunGone, resumeRun } from '../api/resume';
import { newIdempotencyKey } from '../api/idempotency';
import type { LastPosition, SessionView } from '../api/types';
import { initialState, reducer, storePlayer } from './gameReducer';
import type { Action, State } from './gameReducer';

// Global UI state shared by every screen. The reducer and state types live in
// gameReducer.ts; this file only wires them into React.

export interface RunOptions {
  /** A rejected world event: where the server last saw the player (§4.11). */
  onRejected?: (position: LastPosition) => void;
}

/** An API call that changes the game; it sends `key` as its Idempotency-Key (P-56). */
export type GameAction = (key: string) => Promise<SessionView>;

export interface GameContextValue {
  state: State;
  dispatch: Dispatch<Action>;
  /** Runs an action with a new idempotency key; resolves to true once it was applied. */
  run: (action: GameAction, options?: RunOptions) => Promise<boolean>;
  /** Gets back into the current run, waiting out outages (P-30); resolves to the server's view, or null. */
  resume: () => Promise<SessionView | null>;
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
  /** The resume under way: every caller waits for the same one. */
  const resuming = useRef<Promise<SessionView | null> | null>(null);

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

  const resume = useCallback((): Promise<SessionView | null> => {
    if (!getSessionId()) return Promise.resolve(null);
    if (resuming.current) return resuming.current;
    const controller = new AbortController();
    reconnecting.current = controller;
    const resumed: Promise<SessionView | null> = resumeRun(api.getGame, {
      signal: controller.signal,
      onRetry: () => dispatch({ type: 'SET_RECONNECTING', reconnecting: true }),
    }).then((outcome) => {
      if (controller.signal.aborted) return null; // the player left, or React remounted
      if (outcome.kind === 'gone') runGone();
      if (outcome.kind !== 'resumed') return null;
      dispatch({ type: 'SET_VIEW', view: outcome.view });
      return outcome.view;
    }).finally(() => {
      if (reconnecting.current === controller) reconnecting.current = null;
      if (resuming.current === resumed) resuming.current = null;
      dispatch({ type: 'SET_RECONNECTING', reconnecting: false });
    });
    resuming.current = resumed;
    return resumed;
  }, [runGone]);

  const leaveToTitle = useCallback(() => {
    reconnecting.current?.abort();
    reconnecting.current = null;
    resuming.current = null;
    dispatch({ type: 'SET_RECONNECTING', reconnecting: false });
    dispatch({ type: 'GO', screen: 'title' });
  }, []);

  // A failed action: a rule the server refused becomes an error toast (a rejected
  // world event also moves the player back); anything else is shown as it is.
  const refused = useCallback((err: unknown, options: RunOptions) => {
    if (isRunGone(err)) {
      runGone();
      return;
    }
    if (err instanceof ApiError && err.lastPosition && options.onRejected) options.onRejected(err.lastPosition);
    dispatch({ type: 'SET_ERROR', message: errorMessage(err) });
  }, [runGone]);

  // Runs an action with a new idempotency key and stores the server's view. After an
  // outage it waits for the game to reconnect, then sends the same request with the
  // same key: the server applies it if it never arrived, and only answers if it did (P-56).
  const run = useCallback(async (action: GameAction, options: RunOptions = {}) => {
    const key = newIdempotencyKey();
    dispatch({ type: 'SET_BUSY', busy: true });
    try {
      dispatch({ type: 'SET_VIEW', view: await action(key) });
      return true;
    } catch (err) {
      if (!isTransient(err)) {
        refused(err, options);
        return false;
      }
      if (!(await resume())) return false;
      try {
        dispatch({ type: 'SET_VIEW', view: await action(key) });
        return true;
      } catch (retryError) {
        if (isTransient(retryError)) void resume();
        else refused(retryError, options);
        return false;
      }
    } finally {
      dispatch({ type: 'SET_BUSY', busy: false });
    }
  }, [resume, refused]);

  // Leaves the current game: forget the session and go back to the title.
  const quit = useCallback(() => {
    reconnecting.current?.abort();
    resuming.current = null;
    setSessionId(null);
    dispatch({ type: 'QUIT' });
  }, []);

  // After a page load, get back into the game this tab was playing (if any).
  useEffect(() => {
    if (initialState.player) void resume();
    return () => {
      // React StrictMode mounts twice in development: stop this loop so the next mount starts its own.
      reconnecting.current?.abort();
      reconnecting.current = null;
      resuming.current = null;
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

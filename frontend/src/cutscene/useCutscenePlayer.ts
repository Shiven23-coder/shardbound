import { useCallback, useEffect, useRef, useState } from 'react';
import { audio } from '../audio/audio';
import type { Effect } from '../audio/audio';
import { keyIntent } from '../dialogue/dialogueInput';
import { padState } from '../components/gamepadChoices';
import type { CutsceneView } from '../api/types';
import type { WorldBridge } from '../world/bridge';
import { holdProgress, speakerOf, stepRole } from './cutscene';
import type { Speaker } from './cutscene';

// Plays a cutscene (P-68): its cast goes on stage, then each step in turn.
// A line waits for a tap (E, Ⓐ, Enter, Space or a tap on the box), which first
// completes the typing and then moves on; music and sounds start and move on;
// the scene stages the rest and says when it is done. Holding E, Ⓐ or the Skip
// button for HOLD_TO_SKIP_MS skips the rest, with a ring that fills as it is
// held and a "Hold to skip" hint from the first press. Played out or skipped,
// the cutscene ends with onFinish, once.

/** A scene step that never reports back (its scene not ready yet) moves on after its own time and this much more. */
const STEP_GRACE_MS = 3000;
/** A press let go sooner than this is a tap: it advances a line. */
const TAP_MS = 400;
/** The "Hold to skip" hint stays this long after a press is let go. */
const HINT_MS = 2500;

export interface CutsceneLine extends Speaker {
  /** Changes with every line, so the typewriter starts over. */
  key: string;
  text: string;
}

export interface CutscenePlayer {
  /** The line on screen, or null while the scene stages a step. */
  line: CutsceneLine | null;
  /** The line has typed out. */
  typed: boolean;
  onTyped: () => void;
  /** A tap: completes the line's typing, or moves on from a typed line. */
  advance: () => void;
  /** The track the cutscene asked for, or null for the region's. */
  music: string | null;
  /** How far a hold to skip has come, 0 to 1. */
  hold: number;
  /** "Hold to skip" shows. */
  hinted: boolean;
  /** A press of the Skip button, E or Ⓐ begins. */
  press: () => void;
  /** The press ends; `fromButton` presses never advance a line. */
  release: (fromButton: boolean) => void;
}

export function useCutscenePlayer(
  cutscene: CutsceneView | null,
  bridge: WorldBridge,
  onFinish: (cutsceneId: string) => void,
): CutscenePlayer {
  const id = cutscene?.id ?? null;
  const current = useRef(cutscene);
  current.current = cutscene;
  const finishWith = useRef(onFinish);
  finishWith.current = onFinish;
  const finished = useRef<string | null>(null);

  const [at, setAt] = useState({ id, index: 0 });
  const index = at.id === id ? at.index : 0;
  const [typed, setTyped] = useState(false);
  const [music, setMusic] = useState<string | null>(null);
  const [heldSince, setHeldSince] = useState<number | null>(null);
  const [now, setNow] = useState(0);
  const [hinted, setHinted] = useState(false);
  const hintTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const over = () => id === null || finished.current === id;
  const finish = useCallback(() => {
    if (id === null || finished.current === id) return;
    finished.current = id;
    setHeldSince(null);
    clearTimeout(hintTimer.current);
    setHinted(false);                                  // a skip ends the hold: the hint goes with it
    finishWith.current(id);
  }, [id]);
  const next = useCallback(() => {
    if (id === null || finished.current === id) return;
    setAt((a) => ({ id, index: (a.id === id ? a.index : 0) + 1 }));
    setTyped(false);
  }, [id]);

  // The cast takes the stage before the first step; a new cutscene starts on the region's music.
  useEffect(() => {
    const playing = current.current;
    bridge.setCutscene(playing ? { id: playing.id, actors: playing.actors } : null);
    setMusic(null);
    setHinted(false);
  }, [bridge, id]);

  useEffect(() => {
    const playing = current.current;
    if (!playing || id === null || finished.current === id) return undefined;
    const step = playing.steps[index];
    if (!step) {
      finish();
      return undefined;
    }
    const role = stepRole(step);
    if (role === 'say') return undefined;
    if (role === 'audio') {
      if (step.kind === 'MUSIC') setMusic(step.track ?? null);
      else if (step.effect) audio.play(step.effect as Effect);
      next();
      return undefined;
    }
    const off = bridge.toReact.on('cutsceneStepDone', next);
    bridge.toScene.emit('cutsceneStep', step);
    const fallback = setTimeout(next, (step.ms ?? 0) + STEP_GRACE_MS);
    return () => {
      off();
      clearTimeout(fallback);
    };
  }, [bridge, id, index, next, finish]);

  const step = cutscene?.steps[index];
  const line: CutsceneLine | null = cutscene && step && stepRole(step) === 'say'
    ? { key: `${cutscene.id}:${index}`, text: step.text ?? '', ...speakerOf(cutscene, step) }
    : null;

  const advance = () => {
    if (!line || over()) return;
    if (!typed) setTyped(true);
    else next();
  };

  const press = () => {
    if (over() || heldSince !== null) return;
    clearTimeout(hintTimer.current);
    setHinted(true);
    const start = performance.now();
    setHeldSince(start);
    setNow(start);
  };
  const release = (fromButton: boolean) => {
    if (heldSince === null) return;
    const tap = performance.now() - heldSince < TAP_MS;
    setHeldSince(null);
    hintTimer.current = setTimeout(() => setHinted(false), HINT_MS);
    if (tap && !fromButton) advance();
  };
  useEffect(() => () => clearTimeout(hintTimer.current), []);

  // The ring fills while a press is held; full, the cutscene is skipped.
  useEffect(() => {
    if (heldSince === null) return undefined;
    let frame = requestAnimationFrame(function tick(time) {
      setNow(time);
      if (holdProgress(heldSince, time) >= 1) finish();
      else frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  }, [heldSince, finish]);

  // E, Space and Enter press and let go; the world and the pause menu never see them.
  const keys = useRef({ press, release });
  keys.current = { press, release };
  const playing = id !== null;
  useEffect(() => {
    if (!playing) return undefined;
    const primary = (event: KeyboardEvent) =>
      !event.ctrlKey && !event.metaKey && !event.altKey && !document.querySelector('[aria-modal="true"]')
      && keyIntent(event.key)?.kind === 'primary';
    const onDown = (event: KeyboardEvent) => {
      if (!primary(event)) return;
      event.preventDefault();
      event.stopPropagation();
      if (!event.repeat) keys.current.press();
    };
    const onUp = (event: KeyboardEvent) => {
      if (!primary(event)) return;
      event.preventDefault();
      keys.current.release(false);
    };
    window.addEventListener('keydown', onDown, true);
    window.addEventListener('keyup', onUp, true);
    return () => {
      window.removeEventListener('keydown', onDown, true);
      window.removeEventListener('keyup', onUp, true);
    };
  }, [playing]);

  // Ⓐ on a gamepad presses and lets go the same way.
  useEffect(() => {
    if (!playing || typeof navigator === 'undefined' || !navigator.getGamepads) return undefined;
    let down = true;                                   // a held A must be let go first
    let frame = requestAnimationFrame(function poll() {
      const pad = navigator.getGamepads().find((p) => p !== null);
      const confirm = pad ? padState(pad).confirm : false;
      if (confirm && !down) keys.current.press();
      if (!confirm && down) keys.current.release(false);
      down = confirm;
      frame = requestAnimationFrame(poll);
    });
    return () => cancelAnimationFrame(frame);
  }, [playing]);

  return {
    line,
    typed,
    onTyped: () => setTyped(true),
    advance,
    music,
    hold: holdProgress(heldSince, Math.max(now, heldSince ?? 0)),
    hinted,
    press,
    release,
  };
}

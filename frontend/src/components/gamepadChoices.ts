import { useEffect, useRef } from 'react';
import type { Intent } from '../dialogue/dialogueInput';

// Gamepad control for dialogues: D-pad or left stick up/down move the choice
// highlight, A completes or turns a page and takes the highlighted choice, and
// holding A repeats (fast-forward through text already read, P-44). Walking
// with a gamepad is handled by the Phaser scene.

export type ChoiceIntent = 'up' | 'down' | 'confirm' | null;

/** The buttons and stick of a standard-mapping gamepad that matter here. */
export interface PadState {
  up: boolean;
  down: boolean;
  confirm: boolean;
}

const STICK_DEAD_ZONE = 0.5;
// Standard mapping: button 0 is A, 12 and 13 are D-pad up and down; axis 1 is the left stick's y.
const A = 0;
const DPAD_UP = 12;
const DPAD_DOWN = 13;
/** A held A starts repeating after this long, then repeats this often. */
const HOLD_MS = 450;
const REPEAT_MS = 90;

export function padState(pad: Pick<Gamepad, 'buttons' | 'axes'>): PadState {
  const pressed = (index: number) => pad.buttons[index]?.pressed ?? false;
  const stickY = pad.axes[1] ?? 0;
  return {
    up: pressed(DPAD_UP) || stickY < -STICK_DEAD_ZONE,
    down: pressed(DPAD_DOWN) || stickY > STICK_DEAD_ZONE,
    confirm: pressed(A),
  };
}

/** What a change between two polls means: only a fresh press counts. */
export function choiceIntent(previous: PadState, current: PadState): ChoiceIntent {
  if (current.confirm && !previous.confirm) return 'confirm';
  if (current.up && !previous.up) return 'up';
  if (current.down && !previous.down) return 'down';
  return null;
}

const RELEASED: PadState = { up: false, down: false, confirm: true };   // a held A must be let go first
const INTENTS: Record<NonNullable<ChoiceIntent>, Intent> = {
  confirm: { kind: 'primary' }, up: { kind: 'up' }, down: { kind: 'down' },
};

/** Polls the first gamepad while mounted and reports intents; `held` marks the repeats of a held A. */
export function useGamepadIntents(onIntent: (intent: Intent, held: boolean) => void): void {
  const handler = useRef(onIntent);
  handler.current = onIntent;

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.getGamepads) return undefined;
    let previous = RELEASED;
    let heldSince = 0;
    let lastRepeat = 0;
    let frame = requestAnimationFrame(function poll(now) {
      const pad = navigator.getGamepads().find((p) => p !== null);
      if (pad) {
        const current = padState(pad);
        const intent = choiceIntent(previous, current);
        if (intent) handler.current(INTENTS[intent], false);
        if (intent === 'confirm') heldSince = now;
        if (current.confirm && previous.confirm && heldSince > 0 && now - heldSince > HOLD_MS && now - lastRepeat > REPEAT_MS) {
          lastRepeat = now;
          handler.current({ kind: 'primary' }, true);
        }
        if (!current.confirm) heldSince = 0;
        previous = current;
      }
      frame = requestAnimationFrame(poll);
    });
    return () => cancelAnimationFrame(frame);
  }, []);
}

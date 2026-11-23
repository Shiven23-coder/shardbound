import { useEffect, useRef } from 'react';
import type { Intent } from '../dialogue/dialogueInput';

// Gamepad control for dialogues and menus: D-pad or left stick up/down
// move the highlight, left/right are − and + on the level-up screen (P-61),
// A completes or turns a page and takes the highlighted choice, B goes back
// (battle submenus, P-57), and holding A repeats
// (fast-forward through text already read, P-44). Walking with a gamepad is
// handled by the Phaser scene.

export type ChoiceIntent = 'up' | 'down' | 'left' | 'right' | 'confirm' | 'back' | null;

/** The buttons and stick of a standard-mapping gamepad that matter here. */
export interface PadState {
  up: boolean;
  down: boolean;
  left: boolean;
  right: boolean;
  confirm: boolean;
  back: boolean;
}

const STICK_DEAD_ZONE = 0.5;
// Standard mapping: button 0 is A, 1 is B, 12–15 are the D-pad (up, down, left, right); axes 0 and 1 are the left stick.
const A = 0;
const B = 1;
const DPAD_UP = 12;
const DPAD_DOWN = 13;
const DPAD_LEFT = 14;
const DPAD_RIGHT = 15;
/** A held A starts repeating after this long, then repeats this often. */
const HOLD_MS = 450;
const REPEAT_MS = 90;

export function padState(pad: Pick<Gamepad, 'buttons' | 'axes'>): PadState {
  const pressed = (index: number) => pad.buttons[index]?.pressed ?? false;
  const stickX = pad.axes[0] ?? 0;
  const stickY = pad.axes[1] ?? 0;
  return {
    up: pressed(DPAD_UP) || stickY < -STICK_DEAD_ZONE,
    down: pressed(DPAD_DOWN) || stickY > STICK_DEAD_ZONE,
    left: pressed(DPAD_LEFT) || stickX < -STICK_DEAD_ZONE,
    right: pressed(DPAD_RIGHT) || stickX > STICK_DEAD_ZONE,
    confirm: pressed(A),
    back: pressed(B),
  };
}

/** What a change between two polls means: only a fresh press counts. */
export function choiceIntent(previous: PadState, current: PadState): ChoiceIntent {
  if (current.confirm && !previous.confirm) return 'confirm';
  if (current.back && !previous.back) return 'back';
  if (current.up && !previous.up) return 'up';
  if (current.down && !previous.down) return 'down';
  if (current.left && !previous.left) return 'left';
  if (current.right && !previous.right) return 'right';
  return null;
}

const RELEASED: PadState = { up: false, down: false, left: false, right: false, confirm: true, back: true };   // a held A or B must be let go first
const INTENTS: Record<NonNullable<ChoiceIntent>, Intent> = {
  confirm: { kind: 'primary' }, up: { kind: 'up' }, down: { kind: 'down' }, back: { kind: 'back' },
  left: { kind: 'left' }, right: { kind: 'right' },
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

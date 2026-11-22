import { useEffect, useState } from 'react';

// Input-aware layouts (P-40): the layout follows how the player is playing,
// not the screen width. Touch devices start in touch mode; any key press or
// mouse click switches to the keyboard layout, any touch switches back, live.

/** How the player is giving input right now. */
export type InputMode = 'touch' | 'keyboard';
/** The device to name in hints and button labels (E, gamepad A, the tap icon). */
export type InputDevice = 'keyboard' | 'gamepad' | 'touch';
export type Layout = 'desktop' | 'portrait' | 'landscape';

export interface InputState {
  mode: InputMode;
  device: InputDevice;
  layout: Layout;
}

/** The starting mode from what the device can do: a coarse primary pointer means touch. */
export function initialMode(coarsePointer: boolean): InputMode {
  return coarsePointer ? 'touch' : 'keyboard';
}

/** What the mode switch needs to know about an input event. */
export interface InputEventInfo {
  type: string;
  pointerType?: string;
  /** The key pressed; on-screen keyboards often report 'Unidentified'. */
  key?: string;
  /** The event happened in a text field. */
  inTextField?: boolean;
}

/**
 * The mode after an input event, or the same mode when the event says nothing new.
 * Typing in a text field says nothing about how the game is played (phones'
 * on-screen keyboards send key events too). A mouse click means keyboard-and-mouse
 * only on a device that has a fine pointer: touch-only phones also send
 * compatibility mouse events.
 */
export function modeAfter(current: InputMode, event: InputEventInfo, finePointer: boolean): InputMode {
  if (event.type === 'keydown') {
    return event.inTextField || event.key === 'Unidentified' ? current : 'keyboard';
  }
  if (event.type === 'pointerdown') {
    if (event.pointerType === 'touch' || event.pointerType === 'pen') return 'touch';
    if (event.pointerType === 'mouse' && finePointer) return 'keyboard';
  }
  return current;
}

/** Keyboard and mouse fill the screen with the game; touch splits by orientation. */
export function layoutFor(mode: InputMode, width: number, height: number): Layout {
  if (mode === 'keyboard') return 'desktop';
  return height > width ? 'portrait' : 'landscape';
}

function coarse(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;
}

function anyFine(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(any-pointer: fine)').matches;
}

function current(mode: InputMode, device: InputDevice): InputState {
  return { mode, device, layout: layoutFor(mode, window.innerWidth, window.innerHeight) };
}

/**
 * The live input state. Key presses and mouse clicks mean keyboard; touches
 * mean touch; a gamepad button names the gamepad in hints without changing
 * the layout. Rotating the phone switches between portrait and landscape.
 */
export function useInputState(): InputState {
  const [state, setState] = useState<InputState>(() => {
    const mode = initialMode(coarse());
    return current(mode, mode === 'touch' ? 'touch' : 'keyboard');
  });

  useEffect(() => {
    const onInput = (event: KeyboardEvent | PointerEvent) => {
      setState((previous) => {
        const info: InputEventInfo = {
          type: event.type,
          pointerType: (event as PointerEvent).pointerType,
          key: (event as KeyboardEvent).key,
          inTextField: isTextField(event.target),
        };
        const mode = modeAfter(previous.mode, info, anyFine());
        if (mode === previous.mode && previous.device !== 'gamepad') return previous;
        const device: InputDevice = mode === 'touch' ? 'touch' : 'keyboard';
        const next = current(mode, device);
        return sameState(previous, next) ? previous : next;
      });
    };
    const onResize = () => setState((previous) => {
      const next = current(previous.mode, previous.device);
      return sameState(previous, next) ? previous : next;
    });
    // A gamepad has no events of its own: poll its buttons while one is connected.
    let frame = 0;
    const pollPads = () => {
      const pressed = navigator.getGamepads?.().some((pad) => pad?.buttons.some((b) => b.pressed));
      if (pressed) setState((previous) => (previous.device === 'gamepad' ? previous : { ...previous, device: 'gamepad' }));
      frame = requestAnimationFrame(pollPads);
    };
    const onPadConnected = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(pollPads);
    };
    window.addEventListener('keydown', onInput, true);
    window.addEventListener('pointerdown', onInput, true);
    window.addEventListener('resize', onResize);
    window.addEventListener('gamepadconnected', onPadConnected);
    return () => {
      window.removeEventListener('keydown', onInput, true);
      window.removeEventListener('pointerdown', onInput, true);
      window.removeEventListener('resize', onResize);
      window.removeEventListener('gamepadconnected', onPadConnected);
      cancelAnimationFrame(frame);
    };
  }, []);

  return state;
}

function isTextField(target: EventTarget | null): boolean {
  return target instanceof HTMLElement
    && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
}

function sameState(a: InputState, b: InputState): boolean {
  return a.mode === b.mode && a.device === b.device && a.layout === b.layout;
}

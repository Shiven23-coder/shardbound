// The keyboard for walking and interacting (P-51), read from the DOM events
// themselves. Every key-down of a walking key is exactly one press, however
// many arrive within one frame; Phaser's keyboard plugin replays its whole
// queue of the frame on each new event, which turned a quick burst of taps
// (or fast taps on a slow phone) into extra steps. No Phaser here.
import type { Direction } from './bridge';

const WALK_KEYS: Record<string, Direction> = {
  ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
  w: 'UP', s: 'DOWN', a: 'LEFT', d: 'RIGHT',
};
const INTERACT_KEYS = new Set(['e', ' ', 'Enter']);

export interface KeyboardHandlers {
  /** A walking key went down: one step or turn. */
  press(direction: Direction): void;
  /** E, Space or Enter went down. */
  interact(): void;
}

export class KeyboardInput {
  /** Walking keys held now, the latest last. */
  private readonly down: string[] = [];

  constructor(private readonly target: EventTarget, private readonly handlers: KeyboardHandlers) {
    target.addEventListener('keydown', this.onKeyDown);
    target.addEventListener('keyup', this.onKeyUp);
    target.addEventListener('blur', this.onBlur);
  }

  /** The walking direction held now: the one pressed last, or null. */
  held(): Direction | null {
    const last = this.down.at(-1);
    return last === undefined ? null : WALK_KEYS[last]!;
  }

  dispose(): void {
    this.target.removeEventListener('keydown', this.onKeyDown);
    this.target.removeEventListener('keyup', this.onKeyUp);
    this.target.removeEventListener('blur', this.onBlur);
    this.down.length = 0;
  }

  private readonly onKeyDown = (event: Event): void => {
    const { repeat } = event as KeyboardEvent;
    const key = keyName(event);
    if (repeat || isTextField(event.target)) return;
    const direction = WALK_KEYS[key];
    if (direction) {
      this.release(key);
      this.down.push(key);
      this.handlers.press(direction);
    } else if (INTERACT_KEYS.has(key)) {
      this.handlers.interact();
    }
  };

  private readonly onKeyUp = (event: Event): void => {
    this.release(keyName(event));
  };

  private readonly onBlur = (): void => {
    this.down.length = 0;
  };

  private release(key: string): void {
    const index = this.down.indexOf(key);
    if (index >= 0) this.down.splice(index, 1);
  }
}

/** The key, with letters in lower case, so Shift or Caps Lock do not change which key it is. */
function keyName(event: Event): string {
  const { key } = event as KeyboardEvent;
  return key.length === 1 ? key.toLowerCase() : key;
}

function isTextField(target: EventTarget | null): boolean {
  return typeof HTMLElement !== 'undefined' && target instanceof HTMLElement
    && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
}

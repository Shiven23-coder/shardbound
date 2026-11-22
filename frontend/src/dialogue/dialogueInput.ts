// What a key or button press does in an open dialogue (P-44). Pure, so the
// keyboard, the gamepad and taps share one set of rules.

/** The player's intent, whatever the device. */
export type Intent = { kind: 'primary' } | { kind: 'up' } | { kind: 'down' } | { kind: 'pick'; index: number } | { kind: 'back' };

/** What the dialogue box should do. */
export type DialogueAction =
  | { kind: 'complete' }               // show the rest of the page at once
  | { kind: 'next' }                   // turn to the next page
  | { kind: 'highlight'; index: number }
  | { kind: 'choose'; index: number };

/** Where the dialogue stands right now. */
export interface DialogueMoment {
  /** The current page has finished typing. */
  pageDone: boolean;
  lastPage: boolean;
  /** This node was read before on this profile: a held key may skip through it. */
  alreadyRead: boolean;
  /** One entry per choice: true when it can be taken (not locked). */
  choices: boolean[];
  highlight: number;
}

const MAX_NUMBERED = 4;

/** The intent of a key, or null for keys the dialogue ignores. */
export function keyIntent(key: string): Intent | null {
  if (key === 'e' || key === 'E' || key === ' ' || key === 'Enter') return { kind: 'primary' };
  if (key === 'w' || key === 'W' || key === 'ArrowUp') return { kind: 'up' };
  if (key === 's' || key === 'S' || key === 'ArrowDown') return { kind: 'down' };
  if (key === 'Escape' || key === 'Backspace') return { kind: 'back' };
  const number = Number(key);
  if (Number.isInteger(number) && number >= 1 && number <= MAX_NUMBERED) return { kind: 'pick', index: number - 1 };
  return null;
}

/**
 * The action for an intent. `held` is an auto-repeat from a key held down: it
 * only fast-forwards text already read, and never takes a choice.
 */
export function dialogueAction(moment: DialogueMoment, intent: Intent, held: boolean): DialogueAction | null {
  if (intent.kind === 'back') return null;                // a dialogue has no "back"; Esc stays the menu key
  const choosing = moment.pageDone && moment.lastPage;
  if (intent.kind === 'primary') {
    if (held && (!moment.alreadyRead || choosing)) return null;
    if (!moment.pageDone) return { kind: 'complete' };
    if (!moment.lastPage) return { kind: 'next' };
    return moment.choices[moment.highlight] ? { kind: 'choose', index: moment.highlight } : null;
  }
  if (!choosing) return null;
  if (intent.kind === 'pick') {
    return moment.choices[intent.index] ? { kind: 'choose', index: intent.index } : null;
  }
  const index = nextOpen(moment.choices, moment.highlight, intent.kind === 'up' ? -1 : 1);
  return index < 0 ? null : { kind: 'highlight', index };
}

/** The first choice that can be taken, or -1. */
export function firstOpen(choices: boolean[]): number {
  return choices.indexOf(true);
}

/** The next takeable choice from `from` in direction `step`, wrapping around; -1 if there is none. */
function nextOpen(choices: boolean[], from: number, step: number): number {
  for (let i = 1; i <= choices.length; i++) {
    const index = (((from + step * i) % choices.length) + choices.length) % choices.length;
    if (choices[index]) return index;
  }
  return -1;
}

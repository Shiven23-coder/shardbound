// What may show over the world right now (P-49): never more than one of the
// area title card, a dialogue, the objective banner and a hint. On arrival the
// title card comes first, then any opening dialogue, then the objective, and a
// hint only when none of them is up. A battle on screen (a finished one too,
// until its Continue) shows none of them: the dialogue that follows a fight
// opens only once the battle is closed (P-57).

export type Overlay = 'title' | 'dialogue' | 'objective' | 'hint';

/** Which overlays are waiting to be shown. */
export interface Waiting {
  battle: boolean;
  title: boolean;
  dialogue: boolean;
  objective: boolean;
  hint: boolean;
}

export function overlayNow(waiting: Waiting): Overlay | null {
  if (waiting.battle) return null;
  if (waiting.title) return 'title';
  if (waiting.dialogue) return 'dialogue';
  if (waiting.objective) return 'objective';
  if (waiting.hint) return 'hint';
  return null;
}

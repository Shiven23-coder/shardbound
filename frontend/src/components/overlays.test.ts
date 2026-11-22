import { describe, expect, it } from 'vitest';
import { overlayNow } from './overlays';

const nothing = { battle: false, title: false, dialogue: false, objective: false, hint: false };

describe('one overlay at a time on arrival (P-49)', () => {
  it('shows the area title card first, even with a dialogue and an objective waiting', () => {
    expect(overlayNow({ battle: false, title: true, dialogue: true, objective: true, hint: true })).toBe('title');
  });

  it('then the opening dialogue, while the objective waits', () => {
    expect(overlayNow({ ...nothing, dialogue: true, objective: true, hint: true })).toBe('dialogue');
  });

  it('then the objective once the dialogue has closed, before any hint', () => {
    expect(overlayNow({ ...nothing, objective: true, hint: true })).toBe('objective');
    expect(overlayNow({ ...nothing, hint: true })).toBe('hint');
    expect(overlayNow(nothing)).toBeNull();
  });

  it('shows nothing over a battle: the dialogue after a fight waits for its Continue (P-57)', () => {
    expect(overlayNow({ battle: true, title: true, dialogue: true, objective: true, hint: true })).toBeNull();
  });
});

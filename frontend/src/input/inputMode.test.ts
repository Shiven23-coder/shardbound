import { describe, expect, it } from 'vitest';
import { initialMode, layoutFor, modeAfter } from './inputMode';

describe('input-aware layouts', () => {
  it('starts in touch mode on a coarse pointer, keyboard otherwise', () => {
    expect(initialMode(true)).toBe('touch');
    expect(initialMode(false)).toBe('keyboard');
  });

  it('switches on the last input used', () => {
    expect(modeAfter('touch', { type: 'keydown' }, true)).toBe('keyboard');
    expect(modeAfter('keyboard', { type: 'pointerdown', pointerType: 'touch' }, true)).toBe('touch');
    expect(modeAfter('touch', { type: 'pointerdown', pointerType: 'mouse' }, true)).toBe('keyboard');
    expect(modeAfter('touch', { type: 'resize' }, true)).toBe('touch');
  });

  it('ignores typing in text fields and keys from on-screen keyboards', () => {
    expect(modeAfter('touch', { type: 'keydown', key: 'a', inTextField: true }, false)).toBe('touch');
    expect(modeAfter('touch', { type: 'keydown', key: 'Unidentified' }, false)).toBe('touch');
    expect(modeAfter('touch', { type: 'keydown', key: 'ArrowUp' }, false)).toBe('keyboard');
  });

  it('ignores mouse clicks on a device without a fine pointer', () => {
    expect(modeAfter('touch', { type: 'pointerdown', pointerType: 'mouse' }, false)).toBe('touch');
    expect(modeAfter('touch', { type: 'keydown' }, false)).toBe('keyboard');
  });

  it('chooses the layout from the mode and the orientation, never the width alone', () => {
    expect(layoutFor('keyboard', 390, 844)).toBe('desktop');
    expect(layoutFor('touch', 390, 844)).toBe('portrait');
    expect(layoutFor('touch', 844, 390)).toBe('landscape');
    expect(layoutFor('touch', 1366, 1024)).toBe('landscape');
  });
});

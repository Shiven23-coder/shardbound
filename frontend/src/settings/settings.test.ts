import { describe, expect, it } from 'vitest';
import { DEFAULT_SETTINGS, parseSettings } from './settings';
import { hintText } from './hints';

describe('settings', () => {
  it('falls back to the defaults for missing, broken or out-of-range values', () => {
    expect(parseSettings(null)).toEqual(DEFAULT_SETTINGS);
    expect(parseSettings('{not json')).toEqual(DEFAULT_SETTINGS);
    expect(parseSettings('{"musicVolume": 140, "muted": "yes"}')).toEqual(DEFAULT_SETTINGS);
    expect(parseSettings('{"musicVolume": 25, "muted": true, "hints": false}'))
      .toEqual({ ...DEFAULT_SETTINGS, musicVolume: 25, muted: true, hints: false });
  });
});

describe('hints', () => {
  it('name the button of the device in use', () => {
    expect(hintText('interact', 'keyboard', true)).toBe('Press E to talk.');
    expect(hintText('interact', 'gamepad', true)).toBe('Press A to talk.');
    expect(hintText('interact', 'touch')).toBe('Tap Ⓐ to look.');
    expect(hintText('move', 'touch')).toBe('Hold the arrows to walk.');
  });
});

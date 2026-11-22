import { describe, expect, it } from 'vitest';
import { busLevels, playableFile } from './audio';
import { DEFAULT_SETTINGS } from '../settings/settings';

describe('music files', () => {
  const track = { file: 'music/eldmere.ogg', fallback: 'music/eldmere.m4a', volume: 0.3 };

  it('plays the OGG where the browser can, else the AAC fallback (P-48)', () => {
    expect(playableFile(track, () => true)).toBe('music/eldmere.ogg');
    expect(playableFile(track, (type) => !type.includes('ogg'))).toBe('music/eldmere.m4a');
  });
});

describe('audio levels from the settings', () => {
  it('maps the volume sliders to gains', () => {
    expect(busLevels({ ...DEFAULT_SETTINGS, musicVolume: 60, effectsVolume: 80 }))
      .toEqual({ master: 1, music: 0.6, effects: 0.8 });
  });

  it('mutes everything through the master gain and keeps the sliders', () => {
    expect(busLevels({ ...DEFAULT_SETTINGS, muted: true, musicVolume: 60 }))
      .toEqual({ master: 0, music: 0.6, effects: 0.8 });
  });
});

import { describe, expect, it } from 'vitest';
import manifest from '../../../content/audio/audio.json';
import { audioPacks, busLevels, playableFile } from './audio';
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

  it('puts every effect in exactly one pack (P-59)', () => {
    const packed = [audioPacks.initial, ...Object.values(audioPacks.regions), audioPacks.battle].flatMap((p) => p.effects);
    expect([...packed].sort()).toEqual(Object.keys(manifest.effects).sort());
  });

  it("loads a region's own music with its pack, and the battle track only with the battle pack (P-57, P-59)", () => {
    for (const [region, pack] of Object.entries(audioPacks.regions)) expect(pack.music).toBe(region);
    expect(audioPacks.battle.music).toBe('battle');
    expect(Object.keys(manifest.music).sort()).toEqual([...Object.keys(audioPacks.regions), 'battle'].sort());
  });
});

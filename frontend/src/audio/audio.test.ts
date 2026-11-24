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

  it('loads every effect in some pack; initial, battle and boss effects in exactly one, region packs may share (P-59, P-64)', () => {
    const regional = new Set(Object.values(audioPacks.regions).flatMap((p) => p.effects));
    const once = [...audioPacks.initial.effects, ...audioPacks.battle.effects,
      ...Object.values(audioPacks.bosses).flatMap((p) => p.effects)];
    expect(new Set(once).size).toBe(once.length);
    expect(once.filter((effect) => regional.has(effect))).toEqual([]);
    expect([...new Set([...once, ...regional])].sort()).toEqual(Object.keys(manifest.effects).sort());
  });

  it("loads a region's own music with its pack, the battle track only with the battle pack, a boss's with its own (P-57, P-59, P-67)", () => {
    for (const [region, pack] of Object.entries(audioPacks.regions)) expect(pack.music).toBe(region);
    expect(audioPacks.battle.music).toBe('battle');
    const bossTracks = Object.values(audioPacks.bosses).map((p) => p.music);
    expect(Object.keys(manifest.music).sort()).toEqual([...Object.keys(audioPacks.regions), 'battle', ...bossTracks].sort());
  });
});

import { useSyncExternalStore } from 'react';

// Player settings kept in this browser until account settings arrive (V2a):
// sound (P-42) and contextual hints (P-41). Storage can be blocked, so every
// access is wrapped and the defaults always work.

export interface Settings {
  muted: boolean;
  /** 0–100 */
  musicVolume: number;
  /** 0–100 */
  effectsVolume: number;
  hints: boolean;
  /** How fast battle text types and moves on (P-57). */
  battleTextSpeed: BattleTextSpeed;
}

export type BattleTextSpeed = 'slow' | 'normal' | 'fast' | 'instant';
export const BATTLE_TEXT_SPEEDS: readonly BattleTextSpeed[] = ['slow', 'normal', 'fast', 'instant'];

export const DEFAULT_SETTINGS: Settings = {
  muted: false, musicVolume: 60, effectsVolume: 80, hints: true, battleTextSpeed: 'fast',
};
const KEY = 'shardbound.settings';

/** Reads stored settings, keeping only known fields with valid values. */
export function parseSettings(raw: string | null): Settings {
  let stored: Partial<Record<keyof Settings, unknown>> = {};
  try {
    stored = raw ? JSON.parse(raw) : {};
  } catch {
    stored = {};
  }
  const volume = (value: unknown, fallback: number) =>
    typeof value === 'number' && value >= 0 && value <= 100 ? Math.round(value) : fallback;
  return {
    muted: typeof stored.muted === 'boolean' ? stored.muted : DEFAULT_SETTINGS.muted,
    musicVolume: volume(stored.musicVolume, DEFAULT_SETTINGS.musicVolume),
    effectsVolume: volume(stored.effectsVolume, DEFAULT_SETTINGS.effectsVolume),
    hints: typeof stored.hints === 'boolean' ? stored.hints : DEFAULT_SETTINGS.hints,
    battleTextSpeed: BATTLE_TEXT_SPEEDS.includes(stored.battleTextSpeed as BattleTextSpeed)
      ? (stored.battleTextSpeed as BattleTextSpeed) : DEFAULT_SETTINGS.battleTextSpeed,
  };
}

function load(): Settings {
  try {
    return parseSettings(localStorage.getItem(KEY));
  } catch {
    return DEFAULT_SETTINGS;
  }
}

let settings: Settings = typeof localStorage === 'undefined' ? DEFAULT_SETTINGS : load();
const listeners = new Set<() => void>();

export function getSettings(): Settings {
  return settings;
}

export function updateSettings(change: Partial<Settings>): void {
  settings = { ...settings, ...change };
  try {
    localStorage.setItem(KEY, JSON.stringify(settings));
  } catch {
    // Storage unavailable: the settings still apply for this visit.
  }
  listeners.forEach((listener) => listener());
}

export function subscribeSettings(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useSettings(): Settings {
  return useSyncExternalStore(subscribeSettings, getSettings, getSettings);
}

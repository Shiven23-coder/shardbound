// Test-only helpers (imported by *.test.ts files, never by the app).
import type { GameView } from '../api/types';

// A small but complete GameView, shaped exactly like the server's JSON
// (the server leaves out null fields, so optional parts are simply absent).
export function makeView(overrides: Partial<GameView> = {}): GameView {
  return {
    sessionId: 'session-1',
    mode: 'STORY',
    region: 'VILLAGE',
    location: 'village_square',
    locationName: 'Village Square',
    chapter: 1,
    demoMode: false,
    node: { id: 'start', title: 'The Square', text: 'Rain falls on the square.' },
    choices: [{ id: 1, label: 'Walk to the well', locked: false }],
    hud: {
      hp: 30, maxHp: 30, energy: 3, maxEnergy: 5, gold: 10, level: 1, xp: 0, xpToNext: 100,
      atk: 4, def: 2,
    },
    inventory: { usedSlots: 0, capacity: 12, items: [] },
    quests: [],
    reputation: [],
    affinity: [],
    journal: [],
    toasts: [{ kind: 'INFO', text: 'Welcome.' }],
    offerReload: false,
    ...overrides,
  };
}

// In-memory stand-in for the browser's sessionStorage (Node has none).
export class MemoryStorage {
  private readonly items = new Map<string, string>();

  getItem(key: string): string | null {
    return this.items.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    this.items.set(key, value);
  }

  removeItem(key: string): void {
    this.items.delete(key);
  }

  clear(): void {
    this.items.clear();
  }
}

// A sessionStorage whose every call throws, like a browser with storage blocked.
export const blockedStorage = {
  getItem(): string | null {
    throw new Error('storage blocked');
  },
  setItem(): void {
    throw new Error('storage blocked');
  },
  removeItem(): void {
    throw new Error('storage blocked');
  },
};

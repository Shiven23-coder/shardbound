// Test-only helpers (imported by *.test.ts files, never by the app).
import type { SessionView } from '../api/types';

// A small but complete SessionView, shaped exactly like the server's JSON
// (the server leaves out null fields, so optional parts are simply absent).
export function makeView(overrides: Partial<SessionView> = {}): SessionView {
  return {
    sessionId: 'session-1',
    mode: 'DIALOGUE',
    demoMode: false,
    world: {
      areaId: 'eldmere_square',
      areaName: 'Eldmere Square',
      regionId: 'eldmere',
      mapKey: 'eldmere_square',
      lightState: 'dawn',
      player: { x: 24, y: 184, facing: 'DOWN' },
      npcs: [{ id: 'maren', npcId: 'maren', x: 120, y: 104 }],
      hiddenObjects: [],
    },
    dialogue: {
      nodeId: 'start',
      title: 'The Square',
      text: 'Rain falls on the square.',
      choices: [{ id: 1, label: 'Walk to the well', locked: false }],
      message: false,
    },
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

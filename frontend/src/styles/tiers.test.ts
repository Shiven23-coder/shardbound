import { describe, expect, it } from 'vitest';

// Read from disk: Vitest's CSS handling empties a `?raw` stylesheet import.
const fs = (globalThis as unknown as { process: { getBuiltinModule: (id: string) => unknown } }).process
  .getBuiltinModule('node:fs') as { readFileSync: (url: URL, encoding: 'utf8') => string };
const css = fs.readFileSync(new URL('./tiers.css', import.meta.url), 'utf8');

// Every tier has its own colour (P-65): F–C climb a metal ladder (ash grey,
// stone, bronze, silver), and no two tiers share a hue family. The badge letter
// stays readable on each (WCAG AA, 4.5:1); SSS's badge is a moving rainbow with a
// shadowed white letter, so its contrast is not measured.

const TIERS = ['F', 'E', 'D', 'C', 'B', 'A', 'S', 'SS'] as const;
const ALL_TIERS = [...TIERS, 'SSS'] as const;

/** The custom property set for a tier by its plain `[data-tier='X'] { … }` rule. */
function tierVar(tier: string, name: string): string | undefined {
  const rule = new RegExp(String.raw`^\[data-tier='${tier}'\]\s*\{([^}]*)\}`, 'm').exec(css)?.[1] ?? '';
  return new RegExp(String.raw`--${name}:\s*(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})`).exec(rule)?.[1];
}

const DEFAULT_TEXT = /\.tier-badge\s*\{[^}]*--tier-text:\s*(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})/.exec(css)![1]!;

function luminance(colour: string): number {
  const hex = colour.length === 4 ? `#${[...colour.slice(1)].map((c) => c + c).join('')}` : colour;
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}

function hsl(colour: string): { h: number; s: number; l: number } {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(colour.slice(i, i + 2), 16) / 255) as [number, number, number];
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  if (d === 0) return { h: 0, s: 0, l };
  const s = d / (1 - Math.abs(2 * l - 1));
  const h = max === r ? 60 * (((g - b) / d + 6) % 6) : max === g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
  return { h, s, l };
}

const FAMILIES: [string, number][] = [
  ['red', 15], ['orange', 40], ['yellow', 70], ['green', 170], ['cyan', 200], ['blue', 250], ['purple', 290], ['magenta', 345],
];

/** A colour's family as a player would name it: a hue, or for greys their lightness and warmth. */
export function hueFamily(colour: string): string {
  const { h, s, l } = hsl(colour);
  if (s < 0.25) return l < 0.5 ? 'dark neutral' : h >= 20 && h <= 90 ? 'warm light neutral' : 'cool light neutral';
  return FAMILIES.find(([, upTo]) => h < upTo)?.[0] ?? 'red';
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi! + 0.05) / (lo! + 0.05);
}

describe('tier colours', () => {
  it('gives every tier a colour of its own, each in a hue family of its own', () => {
    const colours = ALL_TIERS.map((tier) => tierVar(tier, 'tier-color')!);
    expect(colours.every(Boolean)).toBe(true);
    const families = Object.fromEntries(ALL_TIERS.map((tier, i) => [tier, hueFamily(colours[i]!)]));
    expect(new Set(Object.values(families)).size, JSON.stringify(families)).toBe(ALL_TIERS.length);
  });

  it('climbs a metal ladder from F to C: ash grey, stone, bronze, silver', () => {
    expect(['F', 'E', 'D', 'C'].map((tier) => hueFamily(tierVar(tier, 'tier-color')!)))
      .toEqual(['dark neutral', 'warm light neutral', 'orange', 'cool light neutral']);
  });

  it.each(TIERS)('keeps the %s badge letter readable (4.5:1)', (tier) => {
    const text = tierVar(tier, 'tier-text') ?? DEFAULT_TEXT;
    expect(contrast(tierVar(tier, 'tier-color')!, text)).toBeGreaterThanOrEqual(4.5);
  });
});

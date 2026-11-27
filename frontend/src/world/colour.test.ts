import { describe, expect, it } from 'vitest';
import { mixColour } from './colour';

describe('mixColour', () => {
  it('runs from one colour to the other, channel by channel', () => {
    expect(mixColour(0x000000, 0xa04a10, 0)).toBe(0x000000);
    expect(mixColour(0x000000, 0xa04a10, 1)).toBe(0xa04a10);
    expect(mixColour(0x200000, 0x6040ff, 0.5)).toBe(0x402080);
  });
});

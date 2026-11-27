// Colour arithmetic for the scenes' tints. No Phaser here.

/** The colour `share` (0 to 1) of the way from `from` to `to`, channel by channel (0xRRGGBB). */
export function mixColour(from: number, to: number, share: number): number {
  const channel = (shift: number) => {
    const a = (from >> shift) & 0xff;
    const b = (to >> shift) & 0xff;
    return Math.round(a + (b - a) * share) << shift;
  };
  return channel(16) | channel(8) | channel(0);
}

import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import WeaponCard from './WeaponCard';
import type { WeaponView } from '../api/types';

// The card shows the server's numbers for this player (P-65): attack rating, crit and grades.
const hatchet: WeaponView = {
  id: 'woodcutter_hatchet', name: "Woodcutter's Hatchet", tier: 'E', description: 'A heavy hatchet.',
  base: 28, attack: 31, critPct: 5, str: 'C',
};

describe('WeaponCard', () => {
  it('shows the attack rating, crit and grades the server sent', () => {
    const html = renderToStaticMarkup(<WeaponCard weapon={hatchet} />);
    expect(html).toContain('AR 31');
    expect(html).toContain('Crit 5%');
    expect(html).toMatch(/Str[^A-Z]*C/);
    expect(html).not.toMatch(/Dex|Arc/);                    // no grade, no row
    expect(html).toContain('data-tier="E"');
  });

  it('shows the attack rating on the compact line too', () => {
    expect(renderToStaticMarkup(<WeaponCard weapon={hatchet} compact />)).toContain('AR 31');
  });
});

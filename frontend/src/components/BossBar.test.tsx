import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import BossBar from './BossBar';
import BattleInfoBox from './BattleInfoBox';
import type { BossBarView, FighterView } from '../api/types';

// The boss bar (P-67): name, HP, the phase-2 tick, the phase, and the warning of a pending telegraph.
const herald: FighterView = {
  name: 'Ashen Herald', level: 5, hp: 150, maxHp: 200, sprite: 'boss.ashen_herald',
  statuses: [{ id: 'EXPOSED', name: 'Exposed', letter: 'Ex', icon: 'crack', turns: 1 }],
};
const bar: BossBarView = { name: 'Ashen Herald', phase: 1, phase2Pct: 50 };

describe('BossBar', () => {
  it('shows the name, the HP share, the phase-2 tick and the statuses', () => {
    const html = renderToStaticMarkup(<BossBar boss={bar} fighter={herald} phase={1} warning={null} />);
    expect(html).toContain('Ashen Herald');
    expect(html).toContain('width:75%');
    expect(html).toContain('left:50%');
    expect(html).toContain('aria-valuenow="150"');
    expect(html).toContain('status-crack');
    expect(html).not.toContain('next turn');
  });

  it('marks the second phase', () => {
    const html = renderToStaticMarkup(<BossBar boss={{ ...bar, phase: 2 }} fighter={herald} phase={2} warning={null} />);
    expect(html).toContain('boss-phase-2');
    expect(html).toContain('>II<');
  });

  it('warns of a pending telegraphed blow until it lands', () => {
    const html = renderToStaticMarkup(<BossBar boss={bar} fighter={herald} phase={1} warning="Cinderfall" />);
    expect(html).toContain('⚠ Cinderfall next turn');
  });
});

describe('BattleInfoBox', () => {
  it("warns of a creep's pending telegraphed blow too", () => {
    const rat: FighterView = { name: 'Storm Rat', level: 1, hp: 20, maxHp: 20, sprite: 'creep.storm_rat', statuses: [] };
    expect(renderToStaticMarkup(<BattleInfoBox fighter={rat} side="enemy" warning="Frenzy" />)).toContain('next turn');
    expect(renderToStaticMarkup(<BattleInfoBox fighter={rat} side="enemy" />)).not.toContain('next turn');
  });
});

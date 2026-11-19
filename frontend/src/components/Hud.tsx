import StatBar from './StatBar';
import WeaponCard from './WeaponCard';
import type { HudView } from '../api/types';

// Top bar: HP, energy pips, gold, level, XP and the equipped weapon.
// Every number is taken from GameView.hud as-is.
interface HudProps {
  hud: HudView | null;
  demoMode: boolean;
}

export default function Hud({ hud, demoMode }: HudProps) {
  if (!hud) return null;

  return (
    <header className="hud panel">
      <div className="hud-bars">
        <StatBar label="HP" value={hud.hp} max={hud.maxHp} kind="hp" />
        <StatBar label={`Level ${hud.level} · XP`} value={hud.xp} max={hud.xpToNext} kind="xp" />
      </div>
      <div className="hud-stats">
        <EnergyPips energy={hud.energy} maxEnergy={hud.maxEnergy} />
        <span className="hud-gold" title="Gold">
          <span aria-hidden="true">◈</span> {hud.gold} gold
        </span>
        {hud.atk != null && <span className="hud-small">ATK {hud.atk}</span>}
        {hud.def != null && <span className="hud-small">DEF {hud.def}</span>}
        {demoMode && <span className="tag tag-demo">Demo</span>}
      </div>
      <div className="hud-weapon">
        <WeaponCard weapon={hud.weapon} compact />
      </div>
    </header>
  );
}

interface EnergyPipsProps {
  energy: number;
  maxEnergy: number;
}

function EnergyPips({ energy, maxEnergy }: EnergyPipsProps) {
  const pips = Array.from({ length: maxEnergy }, (_, i) => i < energy);
  return (
    <span className="energy" role="img" aria-label={`Energy ${energy} of ${maxEnergy}`}>
      <span className="energy-label" aria-hidden="true">EN</span>
      {pips.map((filled, i) => (
        <span key={i} className={filled ? 'pip pip-full' : 'pip'} aria-hidden="true" />
      ))}
    </span>
  );
}

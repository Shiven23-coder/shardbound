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
        <StatBar label="FP" value={hud.fp} max={hud.maxFp} kind="xp" />
      </div>
      <div className="hud-stats">
        <span className="hud-small">Level {hud.level}</span>
        <span className="hud-shards" title="Shards">
          <span aria-hidden="true">◆</span> {hud.shards} Shards
        </span>
        <span className="hud-gold" title="Gold">
          <span aria-hidden="true">◈</span> {hud.gold} gold
        </span>
        <span className="hud-small">AR {hud.attack}</span>
        <span className="hud-small">DEF {hud.defence}</span>
        {demoMode && <span className="tag tag-demo">Demo</span>}
      </div>
      <div className="hud-weapon">
        <WeaponCard weapon={hud.weapon} compact />
      </div>
    </header>
  );
}

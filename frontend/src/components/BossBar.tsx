import type { BossBarView, FighterView } from '../api/types';
import { StatusIcon } from './BattleInfoBox';

// A boss's bar (P-67): its name, a long HP bar with a tick where its second
// phase begins, the phase it is in, its statuses, and a warning that stays
// while a telegraphed blow is pending. It sits bottom-centre above the battle
// text, or at the top of the deck in portrait. The HP and the phase keep pace
// with the battle text.
interface BossBarProps {
  boss: BossBarView;
  fighter: FighterView;
  phase: number;
  warning: string | null;
}

export default function BossBar({ boss, fighter, phase, warning }: BossBarProps) {
  const share = fighter.maxHp > 0 ? fighter.hp / fighter.maxHp : 0;
  return (
    <section className={`boss-bar boss-phase-${phase}`} aria-label={`${boss.name}, phase ${phase}`}>
      <div className="boss-bar-head">
        <span className="boss-bar-name">{boss.name}</span>
        <span className="boss-bar-phase" aria-hidden="true">{phase === 1 ? 'I' : 'II'}</span>
        {fighter.statuses.length > 0 && (
          <ul className="battle-statuses boss-bar-statuses" aria-label="Statuses">
            {fighter.statuses.map((status) => <StatusIcon key={status.id} status={status} />)}
          </ul>
        )}
      </div>
      <div className="boss-bar-track" role="progressbar" aria-label="HP" aria-valuemin={0} aria-valuemax={fighter.maxHp}
        aria-valuenow={fighter.hp}>
        <span className="boss-bar-fill" style={{ width: `${share * 100}%` }} />
        <span className="boss-bar-tick" style={{ left: `${boss.phase2Pct}%` }} aria-hidden="true" />
      </div>
      {warning && <p className="battle-warning boss-bar-warning" role="status">⚠ {warning} next turn</p>}
    </section>
  );
}

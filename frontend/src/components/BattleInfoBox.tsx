import type { FighterView, StatusView } from '../api/types';

// A fighter's info box (V2_SPEC §1, P-57): name, level, an HP bar that drains
// (CSS transition), the player's HP numbers and FP bar, and status icons, each
// a shape with a letter so colour is never the only cue (§4.9).
interface BattleInfoBoxProps {
  fighter: FighterView;
  side: 'enemy' | 'player';
  fp?: number;
  maxFp?: number;
}

export default function BattleInfoBox({ fighter, side, fp, maxFp }: BattleInfoBoxProps) {
  const share = fighter.maxHp > 0 ? fighter.hp / fighter.maxHp : 0;
  const tone = share > 0.5 ? 'hp-high' : share > 0.2 ? 'hp-mid' : 'hp-low';
  return (
    <section className={`battle-info battle-info-${side}`} aria-label={`${fighter.name}, level ${fighter.level}`}>
      <div className="battle-info-head">
        <span className="battle-info-name">{fighter.name}</span>
        <span className="battle-info-level">Lv {fighter.level}</span>
      </div>
      <div className="battle-bar" role="progressbar" aria-label="HP" aria-valuemin={0} aria-valuemax={fighter.maxHp}
        aria-valuenow={fighter.hp}>
        <span className="battle-bar-label" aria-hidden="true">HP</span>
        <span className="battle-bar-track"><span className={`battle-bar-fill ${tone}`} style={{ width: `${share * 100}%` }} /></span>
      </div>
      {side === 'player' && (
        <>
          <span className="battle-info-numbers">{fighter.hp} / {fighter.maxHp}</span>
          {maxFp !== undefined && fp !== undefined && (
            <div className="battle-bar battle-bar-fp" role="progressbar" aria-label="FP" aria-valuemin={0}
              aria-valuemax={maxFp} aria-valuenow={fp}>
              <span className="battle-bar-label" aria-hidden="true">FP</span>
              <span className="battle-bar-track"><span className="battle-bar-fill fp" style={{ width: `${maxFp ? (fp / maxFp) * 100 : 0}%` }} /></span>
              <span className="battle-fp-numbers">{fp}</span>
            </div>
          )}
        </>
      )}
      {fighter.statuses.length > 0 && (
        <ul className="battle-statuses" aria-label="Statuses">
          {fighter.statuses.map((status) => <StatusIcon key={status.id} status={status} />)}
        </ul>
      )}
    </section>
  );
}

function StatusIcon({ status }: { status: StatusView }) {
  return (
    <li className={`status-icon status-${status.icon}`} title={`${status.name}, ${status.turns} turns`}>
      <span aria-hidden="true">{status.letter}</span>
      <span className="sr-only">{status.name}, {status.turns} turns left</span>
    </li>
  );
}

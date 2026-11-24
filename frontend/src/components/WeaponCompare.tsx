import WeaponCard from './WeaponCard';
import { compareRows } from '../bag/bagMenu';
import type { WeaponView } from '../api/types';

// Compare (P-65): the equipped weapon beside the one chosen, with the server's
// numbers before → after, then Equip or Back. Nothing here waits on the network.
interface WeaponCompareProps {
  current: WeaponView | null;
  candidate: WeaponView;
  highlight: number;
  busy: boolean;
  onEquip: () => void;
  onBack: () => void;
}

export default function WeaponCompare({ current, candidate, highlight, busy, onEquip, onBack }: WeaponCompareProps) {
  return (
    <section className="weapon-compare" aria-label={`Compare ${candidate.name}`}>
      <div className="compare-cards">
        <div className="compare-side">
          <p className="compare-caption">Equipped</p>
          <WeaponCard weapon={current} />
        </div>
        <div className="compare-side">
          <p className="compare-caption">Chosen</p>
          <WeaponCard weapon={candidate} />
        </div>
      </div>
      <table className="compare-table">
        <tbody>
          {compareRows(current, candidate).map((row) => (
            <tr key={row.label} className={`compare-${row.change}`}>
              <th scope="row">{row.label}</th>
              <td>{row.before}</td>
              <td aria-hidden="true">→</td>
              <td className="compare-after">{row.after}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="compare-actions">
        <button type="button" className={`btn${highlight === 0 ? ' bag-highlighted' : ''}`} disabled={busy} onClick={onEquip}>
          Equip
        </button>
        <button type="button" className={`btn btn-ghost${highlight === 1 ? ' bag-highlighted' : ''}`} onClick={onBack}>
          Back
        </button>
      </div>
    </section>
  );
}

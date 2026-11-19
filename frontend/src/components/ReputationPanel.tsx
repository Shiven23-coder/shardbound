import type { AffinityView, ReputationView } from '../api/types';

interface ReputationPanelProps {
  reputation?: ReputationView[];
  affinity?: AffinityView[];
}

// Faction reputation and NPC affinity values, shown exactly as sent.
export default function ReputationPanel({ reputation = [], affinity = [] }: ReputationPanelProps) {
  return (
    <div className="reputation">
      <h3 className="panel-subtitle">Factions</h3>
      <ValueList rows={reputation.map((r) => ({ key: r.faction, name: r.name, value: r.value }))} />
      <h3 className="panel-subtitle">People</h3>
      <ValueList rows={affinity.map((a) => ({ key: a.npcId, name: a.name, value: a.value }))} />
    </div>
  );
}

interface ValueRow {
  key: string;
  name: string;
  value: number;
}

interface ValueListProps {
  rows: ValueRow[];
}

function ValueList({ rows }: ValueListProps) {
  if (rows.length === 0) return <p className="empty">Nobody knows you yet.</p>;

  return (
    <ul className="value-list">
      {rows.map((row) => (
        <li key={row.key} className="value-row">
          <span>{row.name}</span>
          <span className={`value ${signClass(row.value)}`}>
            {row.value > 0 ? `+${row.value}` : row.value}
          </span>
        </li>
      ))}
    </ul>
  );
}

// Colour hint only: green for positive, red for negative.
function signClass(value: number): string {
  if (value > 0) return 'value-pos';
  if (value < 0) return 'value-neg';
  return 'value-zero';
}

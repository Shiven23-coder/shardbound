import type { ReactNode } from 'react';
import type { WeaponView } from '../api/types';

// A weapon with its tier badge. Colours and animations come from tiers.css via
// data-tier; every number is the server's, for this player (P-65).

const GRADES = [['Str', 'str'], ['Dex', 'dex'], ['Arc', 'arc']] as const;

export function TierBadge({ tier }: { tier?: string }) {
  if (!tier) return null;
  return (
    <span className="tier-badge" data-tier={tier} title={`Tier ${tier}`}>
      {tier}
    </span>
  );
}

/** The scaling grades the weapon has, e.g. [['Str', 'C']]. */
export function gradesOf(weapon: WeaponView): [string, string][] {
  return GRADES.flatMap(([label, key]) => (weapon[key] ? [[label, weapon[key]] as [string, string]] : []));
}

// `compact` is the one-line version for the character sheet; `children` holds
// action buttons (e.g. Compare) in the Bag.
interface WeaponCardProps {
  weapon?: WeaponView | null;
  equipped?: boolean;
  compact?: boolean;
  children?: ReactNode;
}

export default function WeaponCard({ weapon, equipped = false, compact = false, children }: WeaponCardProps) {
  if (!weapon) return <span className="weapon-none">Unarmed</span>;

  if (compact) {
    return (
      <span className="weapon-compact">
        <TierBadge tier={weapon.tier} />
        <span className="weapon-name">{weapon.name}</span>
        <span className="weapon-stat">AR {weapon.attack}</span>
      </span>
    );
  }

  return (
    <div className="weapon-card" data-tier={weapon.tier}>
      <div className="weapon-card-inner">
        <div className="weapon-card-head">
          <TierBadge tier={weapon.tier} />
          <strong className="weapon-name">{weapon.name}</strong>
          {equipped && <span className="tag">Equipped</span>}
        </div>
        <p className="weapon-stats">
          <span>AR {weapon.attack}</span>
          <span>Crit {weapon.critPct}%</span>
          {gradesOf(weapon).map(([label, grade]) => (
            <span key={label} className="weapon-grade">
              {label} <b>{grade}</b>
            </span>
          ))}
        </p>
        {weapon.description && <p className="item-desc">{weapon.description}</p>}
        {children}
      </div>
    </div>
  );
}

import type { ReactNode } from 'react';

// Weapon display with a tier badge. All colours/animations come from
// tiers.css via the data-tier attribute; the tier itself comes from Java.

// The fields WeaponCard reads. Both HudView.weapon (WeaponInfo) and an
// inventory ItemView fit this shape; the optional ones exist only on ItemView.
export interface WeaponCardData {
  name: string;
  tier?: string;
  attack?: number;
  critPct?: number;
  equipped?: boolean;
  description?: string;
}

interface TierBadgeProps {
  tier?: string;
}

export function TierBadge({ tier }: TierBadgeProps) {
  if (!tier) return null;
  return (
    <span className="tier-badge" data-tier={tier} title={`Tier ${tier}`}>
      {tier}
    </span>
  );
}

// `compact` renders a one-line version for the HUD.
// `children` is used for action buttons (e.g. Equip) in the inventory.
interface WeaponCardProps {
  weapon?: WeaponCardData;
  compact?: boolean;
  children?: ReactNode;
}

export default function WeaponCard({ weapon, compact = false, children }: WeaponCardProps) {
  if (!weapon) return <span className="weapon-none">Unarmed</span>;

  if (compact) {
    return (
      <span className="weapon-compact">
        <TierBadge tier={weapon.tier} />
        <span className="weapon-name">{weapon.name}</span>
        {weapon.attack != null && <span className="weapon-stat">ATK +{weapon.attack}</span>}
      </span>
    );
  }

  return (
    <div className="weapon-card" data-tier={weapon.tier}>
      <div className="weapon-card-inner">
        <div className="weapon-card-head">
          <TierBadge tier={weapon.tier} />
          <strong className="weapon-name">{weapon.name}</strong>
        </div>
        <p className="weapon-stats">
          {weapon.attack != null && <span>ATK +{weapon.attack}</span>}
          {weapon.critPct != null && <span>Crit {weapon.critPct}%</span>}
          {weapon.equipped && <span className="tag">Equipped</span>}
        </p>
        {weapon.description && <p className="item-desc">{weapon.description}</p>}
        {children}
      </div>
    </div>
  );
}

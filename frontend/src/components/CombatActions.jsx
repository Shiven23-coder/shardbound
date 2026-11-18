import { useState } from 'react';

// The combat action bar. It only sends intents; the server validates them
// (e.g. 409 for SKILL without energy). Disabling Skill when energy < cost is
// a display hint using numbers the server sent.
export default function CombatActions({ combat, hud, busy, onAction }) {
  const [pickingItem, setPickingItem] = useState(false);
  const items = combat.usableItems ?? [];
  const lowEnergy = hud.energy < combat.skillCost;

  function chooseItem(itemId) {
    setPickingItem(false);
    onAction('ITEM', itemId);
  }

  return (
    <div className="combat-actions">
      <div className="combat-buttons">
        <button type="button" className="btn btn-primary" disabled={busy} onClick={() => onAction('ATTACK')}>
          Attack
        </button>
        <button type="button" className="btn" disabled={busy} onClick={() => onAction('DEFEND')}>
          Defend
        </button>
        <button
          type="button"
          className="btn btn-skill"
          disabled={busy || lowEnergy}
          title={lowEnergy ? 'Not enough energy' : undefined}
          onClick={() => onAction('SKILL')}
        >
          Crystal Pulse <span className="skill-cost">({combat.skillCost} EN)</span>
        </button>
        <button
          type="button"
          className="btn"
          disabled={busy || items.length === 0}
          aria-expanded={pickingItem}
          onClick={() => setPickingItem(!pickingItem)}
        >
          Item
        </button>
        {combat.canFlee && (
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => onAction('FLEE')}>
            Flee
          </button>
        )}
      </div>
      {pickingItem && (
        <ul className="item-picker panel" aria-label="Choose an item">
          {items.map((item) => (
            <li key={item.id}>
              <button type="button" className="btn btn-small btn-block" disabled={busy} onClick={() => chooseItem(item.id)}>
                {item.name} ×{item.qty}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

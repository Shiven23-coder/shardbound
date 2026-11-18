import WeaponCard from './WeaponCard.jsx';

// Lists inventory items in the order the server sent them (weapons by tier
// first). `usable` and `equipped` are decided in Java; buttons only follow them.
export default function InventoryPanel({ inventory, onEquip, onUse, busy }) {
  if (!inventory) return <p className="empty">No inventory yet.</p>;
  const { items, usedSlots, capacity } = inventory;

  return (
    <div className="inventory">
      <p className="panel-meta">
        Slots: {usedSlots}/{capacity}
      </p>
      {items.length === 0 && <p className="empty">Your pack is empty.</p>}
      <ul className="item-list">
        {items.map((item) => (
          <li key={item.id}>
            {item.type === 'WEAPON' ? (
              <WeaponCard weapon={item}>
                {!item.equipped && (
                  <button type="button" className="btn btn-small" disabled={busy} onClick={() => onEquip(item.id)}>
                    Equip
                  </button>
                )}
              </WeaponCard>
            ) : (
              <ItemRow item={item} onUse={onUse} busy={busy} />
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

function ItemRow({ item, onUse, busy }) {
  return (
    <div className="item-row">
      <div className="item-row-head">
        <strong>{item.name}</strong>
        {item.qty > 1 && <span className="item-qty">×{item.qty}</span>}
        <span className="tag">{item.type}</span>
      </div>
      <p className="weapon-stats">
        {item.heal != null && <span>Heals {item.heal}</span>}
        {item.energy != null && <span>Energy +{item.energy}</span>}
      </p>
      {item.description && <p className="item-desc">{item.description}</p>}
      {item.usable && (
        <button type="button" className="btn btn-small" disabled={busy} onClick={() => onUse(item.id)}>
          Use
        </button>
      )}
    </div>
  );
}

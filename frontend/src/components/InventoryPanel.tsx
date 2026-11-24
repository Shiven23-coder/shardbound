import WeaponCard from './WeaponCard';
import WeaponCompare from './WeaponCompare';
import type { BagRow, BagState } from '../bag/bagMenu';
import type { InventoryView, ItemView, WeaponView } from '../api/types';

// Lists the items in the order the server sent them (weapons by tier first),
// or Compare for a weapon (P-65). `usable` and `equipped` come from Java; the
// keyboard and gamepad highlight comes from the Bag menu (bag/bagMenu.ts).
interface InventoryPanelProps {
  inventory: InventoryView | null;
  equipped: WeaponView | null;
  state: BagState;
  busy: boolean;
  onCompare: (itemId: string) => void;
  onEquip: (itemId: string) => void;
  onUse: (itemId: string) => void;
  onBack: () => void;
}

/** The menu's view of the items. */
export function bagRows(inventory: InventoryView | null): BagRow[] {
  return (inventory?.items ?? []).map((item) => ({
    id: item.id, weapon: item.weapon != null, equipped: item.equipped, usable: item.usable,
  }));
}

export default function InventoryPanel({
  inventory, equipped, state, busy, onCompare, onEquip, onUse, onBack,
}: InventoryPanelProps) {
  if (!inventory) return <p className="empty">No inventory yet.</p>;
  const { items, usedSlots, capacity } = inventory;

  if (state.page === 'compare') {
    const candidate = items.find((item) => item.id === state.itemId)?.weapon;
    if (candidate) {
      return (
        <WeaponCompare
          current={equipped}
          candidate={candidate}
          highlight={state.highlight}
          busy={busy}
          onEquip={() => onEquip(candidate.id)}
          onBack={onBack}
        />
      );
    }
  }

  return (
    <div className="inventory">
      <p className="panel-meta">
        Slots: {usedSlots}/{capacity}
      </p>
      {items.length === 0 && <p className="empty">Your pack is empty.</p>}
      <ul className="item-list">
        {items.map((item, index) => (
          <li key={item.id} className={state.page === 'list' && index === state.highlight ? 'bag-highlighted' : undefined}>
            {item.weapon ? (
              <WeaponCard weapon={item.weapon} equipped={item.equipped}>
                {!item.equipped && (
                  <button type="button" className="btn btn-small" disabled={busy} onClick={() => onCompare(item.id)}>
                    Compare
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

interface ItemRowProps {
  item: ItemView;
  onUse: (itemId: string) => void;
  busy: boolean;
}

function ItemRow({ item, onUse, busy }: ItemRowProps) {
  return (
    <div className="item-row">
      <div className="item-row-head">
        <strong>{item.name}</strong>
        {item.maxQty ? <span className="item-qty">{item.qty}/{item.maxQty}</span>
          : item.qty > 1 && <span className="item-qty">×{item.qty}</span>}
        <span className="tag">{item.type}</span>
      </div>
      <p className="weapon-stats">
        {item.heal != null && <span>Heals {item.heal}</span>}
        {item.fp != null && item.fp > 0 && <span>FP +{item.fp}</span>}
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

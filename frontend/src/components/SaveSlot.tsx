import { formatDate, formatSeconds } from '../utils/format';
import type { ModalTab } from '../state/gameReducer';
import type { SlotView } from '../api/types';

// One row of the SaveLoadModal. Slot 0 is the autosave: it can be loaded or
// deleted, but not saved to by hand.
interface SaveSlotProps {
  slot: SlotView;
  tab: ModalTab | null;
  working: boolean;
  onSave: (slot: number) => void;
  onLoad: (slot: number) => void;
  onDelete: (slot: number) => void;
}

export default function SaveSlot({ slot, tab, working, onSave, onLoad, onDelete }: SaveSlotProps) {
  const isAutosave = slot.slot === 0;

  return (
    <li className="slot">
      <SlotInfo slot={slot} />
      <div className="slot-actions">
        {tab === 'save' && (
          <button type="button" className="btn btn-small" disabled={working || isAutosave} onClick={() => onSave(slot.slot)}>
            {isAutosave ? 'Auto only' : 'Save'}
          </button>
        )}
        {tab === 'load' && slot.exists && (
          <>
            <button type="button" className="btn btn-small" disabled={working} onClick={() => onLoad(slot.slot)}>
              Load
            </button>
            <button type="button" className="btn btn-small btn-danger" disabled={working} onClick={() => onDelete(slot.slot)}>
              Delete
            </button>
          </>
        )}
      </div>
    </li>
  );
}

interface SlotInfoProps {
  slot: SlotView;
}

function SlotInfo({ slot }: SlotInfoProps) {
  const name = slot.slot === 0 ? 'Autosave' : `Slot ${slot.slot}`;
  if (!slot.exists) {
    return (
      <div className="slot-info">
        <strong>{name}</strong>
        <span className="empty">Empty</span>
      </div>
    );
  }
  return (
    <div className="slot-info">
      <strong>{name}</strong>
      <span>{slot.areaName} · Level {slot.level}</span>
      <small>Played {formatSeconds(slot.playSeconds)} · {formatDate(slot.updatedAt)}</small>
    </div>
  );
}

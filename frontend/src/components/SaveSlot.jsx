import { formatDate, formatSeconds } from '../utils/format.js';

// One row of the SaveLoadModal. Slot 0 is the autosave: it can be loaded or
// deleted, but not saved to by hand.
export default function SaveSlot({ slot, tab, working, onSave, onLoad, onDelete }) {
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

function SlotInfo({ slot }) {
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
      <span>{slot.nodeTitle} · Chapter {slot.chapter} · Level {slot.level}</span>
      <small>Played {formatSeconds(slot.playSeconds)} · {formatDate(slot.updatedAt)}</small>
    </div>
  );
}

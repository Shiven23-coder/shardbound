import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { api, getSessionId } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';
import SaveSlot from './SaveSlot.jsx';

// Save / load dialog. Slot 0 is the autosave (load-only); slots 1-3 are manual.
// The server decides whether saving is allowed (e.g. 409 during combat);
// we just show its message.
export default function SaveLoadModal() {
  const { state, dispatch } = useGame();
  const tab = state.modal;
  const playerId = state.player?.playerId;
  const canSave = Boolean(getSessionId() && state.view);
  const [slots, setSlots] = useState([]);
  const [notice, setNotice] = useState(null); // { text, isError }
  const [working, setWorking] = useState(false);
  const dialogRef = useRef(null);

  const close = useCallback(() => dispatch({ type: 'CLOSE_MODAL' }), [dispatch]);

  const refresh = useCallback(() => {
    if (playerId == null) return;
    api.listSaves(playerId)
      .then(setSlots)
      .catch((err) => setNotice({ text: err.message, isError: true }));
  }, [playerId]);

  useEffect(refresh, [refresh]);
  useEffect(() => setNotice(null), [tab]); // fresh message area per tab

  useEffect(() => {
    dialogRef.current?.focus();
    const onKey = (e) => e.key === 'Escape' && close();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [close]);

  // Runs one slot action and reports the server's answer.
  async function act(action, successText) {
    setWorking(true);
    setNotice(null);
    try {
      const result = await action();
      if (successText) setNotice({ text: successText, isError: false });
      return result;
    } catch (err) {
      setNotice({ text: err.message, isError: true });
      return null;
    } finally {
      setWorking(false);
    }
  }

  async function handleSave(slot) {
    if (await act(() => api.save(slot), `Saved to slot ${slot}.`)) refresh();
  }

  async function handleLoad(slot) {
    const view = await act(() => api.load(slot, playerId));
    if (view) {
      dispatch({ type: 'SET_VIEW', view });
      close();
    }
  }

  async function handleDelete(slot) {
    if (!window.confirm(`Delete the save in slot ${slot}? This cannot be undone.`)) return;
    if (await act(() => api.deleteSave(slot, playerId), `Slot ${slot} deleted.`)) refresh();
  }

  return (
    <div className="modal-backdrop" onClick={close}>
      <motion.div
        ref={dialogRef}
        className="modal panel"
        role="dialog"
        aria-modal="true"
        aria-label="Save and load"
        tabIndex={-1}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="tabs" role="tablist">
          <TabButton label="Save" active={tab === 'save'} disabled={!canSave} onClick={() => dispatch({ type: 'OPEN_MODAL', tab: 'save' })} />
          <TabButton label="Load" active={tab === 'load'} onClick={() => dispatch({ type: 'OPEN_MODAL', tab: 'load' })} />
          <button type="button" className="btn btn-ghost modal-close" onClick={close} aria-label="Close">✕</button>
        </div>
        {playerId == null && <p className="empty">Pick a profile first.</p>}
        {notice && <p className={notice.isError ? 'notice notice-error' : 'notice'} role="status">{notice.text}</p>}
        <ul className="slot-list">
          {slots.map((s) => (
            <SaveSlot
              key={s.slot}
              slot={s}
              tab={tab}
              working={working}
              onSave={handleSave}
              onLoad={handleLoad}
              onDelete={handleDelete}
            />
          ))}
        </ul>
      </motion.div>
    </div>
  );
}

function TabButton({ label, active, disabled, onClick }) {
  return (
    <button type="button" role="tab" aria-selected={active} className={active ? 'tab tab-active' : 'tab'} disabled={disabled} onClick={onClick}>
      {label}
    </button>
  );
}

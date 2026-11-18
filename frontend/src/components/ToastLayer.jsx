import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useGame } from '../state/GameContext.jsx';

const TOAST_MS = 3500;

// Shows the `toasts` array of each new GameView (plus request errors) as a
// stack of messages that slide in and dismiss themselves.
export default function ToastLayer() {
  const { state } = useGame();
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(1);
  const lastView = useRef(null);
  const lastError = useRef(null);

  function push(newToasts) {
    const withIds = newToasts.map((t) => ({ ...t, id: nextId.current++ }));
    setToasts((current) => [...current, ...withIds]);
  }

  // Stable function so each toast's timer is not restarted on re-render.
  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  // Each GameView object is processed once (the ref guards against re-runs).
  useEffect(() => {
    const view = state.view;
    if (!view || view === lastView.current) return;
    lastView.current = view;
    if (view.toasts?.length) push(view.toasts);
  }, [state.view]);

  useEffect(() => {
    const error = state.error;
    if (!error || error === lastError.current) return;
    lastError.current = error;
    push([{ kind: 'ERROR', text: error.message }]);
  }, [state.error]);

  return (
    <div className="toast-layer" aria-live="polite" aria-relevant="additions">
      <AnimatePresence initial={false}>
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} onDone={dismiss} />
        ))}
      </AnimatePresence>
    </div>
  );
}

function Toast({ toast, onDone }) {
  useEffect(() => {
    const timer = setTimeout(() => onDone(toast.id), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast.id, onDone]);

  return (
    <motion.div
      layout
      className="toast"
      data-kind={toast.kind}
      role={toast.kind === 'ERROR' ? 'alert' : 'status'}
      initial={{ x: 140, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      exit={{ x: 140, opacity: 0 }}
      transition={{ type: 'spring', stiffness: 260, damping: 24 }}
      onClick={() => onDone(toast.id)}
    >
      <span className="toast-kind">{toast.kind}</span>
      <span>{toast.text}</span>
    </motion.div>
  );
}

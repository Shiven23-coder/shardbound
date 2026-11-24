import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useGame } from '../state/GameContext';
import type { ErrorState } from '../state/gameReducer';
import type { SessionView, ToastView } from '../api/types';
import { TierBadge } from './WeaponCard';
import { upgradeHint } from '../settings/hints';
import { useInputState, type InputDevice } from '../input/inputMode';

const TOAST_MS = 3500;

// A toast on screen: the server's ToastView plus a local id for React keys.
interface ShownToast extends ToastView {
  id: number;
}

// Shows the `toasts` array of each new SessionView (plus request errors) as a
// stack of messages that slide in and dismiss themselves.
export default function ToastLayer() {
  const { device } = useInputState();
  const { state } = useGame();
  const [toasts, setToasts] = useState<ShownToast[]>([]);
  const nextId = useRef(1);
  const lastView = useRef<SessionView | null>(null);
  const lastError = useRef<ErrorState | null>(null);

  function push(newToasts: ToastView[]) {
    const withIds = newToasts.map((t) => ({ ...t, id: nextId.current++ }));
    setToasts((current) => [...current, ...withIds]);
  }

  // Stable function so each toast's timer is not restarted on re-render.
  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  // Each SessionView object is processed once (the ref guards against re-runs).
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
    push([{ kind: 'ERROR', text: error.message, upgrade: false }]);
  }, [state.error]);

  return (
    <div className="toast-layer" aria-live="polite" aria-relevant="additions">
      <AnimatePresence initial={false}>
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} device={device} onDone={dismiss} />
        ))}
      </AnimatePresence>
    </div>
  );
}

interface ToastProps {
  toast: ShownToast;
  device: InputDevice;
  onDone: (id: number) => void;
}

function Toast({ toast, device, onDone }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(() => onDone(toast.id), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast.id, onDone]);

  return (
    <motion.div
      layout
      className="toast"
      data-kind={toast.kind}
      data-tier={toast.tier ?? undefined}
      role={toast.kind === 'ERROR' ? 'alert' : 'status'}
      initial={{ x: 140, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      exit={{ x: 140, opacity: 0 }}
      transition={{ type: 'spring', stiffness: 260, damping: 24 }}
      onClick={() => onDone(toast.id)}
    >
      <ToastBody toast={toast} device={device} />
    </motion.div>
  );
}

/**
 * A toast's words. A LOOT toast (P-65) shows the find's tier badge inline, its name in the tier
 * colour, and, when the server says it beats the equipped weapon, where to compare it.
 */
export function ToastBody({ toast, device }: { toast: ToastView; device: InputDevice }) {
  const at = toast.subject ? toast.text.indexOf(toast.subject) : -1;
  return (
    <>
      <span className="toast-kind">{toast.kind}</span>
      {at < 0 ? (
        <span>{toast.text}</span>
      ) : (
        <span className="toast-text">
          {toast.text.slice(0, at)}
          {toast.tier && <TierBadge tier={toast.tier} />}{' '}
          <span className="toast-subject" data-tier={toast.tier ?? undefined}>{toast.subject}</span>
          {toast.text.slice(at + toast.subject!.length)}
        </span>
      )}
      {toast.upgrade && <span className="toast-hint">{upgradeHint(device)}</span>}
    </>
  );
}

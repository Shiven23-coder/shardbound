import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { motion } from 'framer-motion';

// A titled dialog over a dimmed backdrop. Esc, the backdrop and ✕ close it.
interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export default function Modal({ title, onClose, children }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    dialogRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      e.stopPropagation(); // the pause menu must not toggle on the same key
      onClose();
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <motion.div
        ref={dialogRef}
        className="modal panel"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="btn btn-ghost modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
      </motion.div>
    </div>
  );
}

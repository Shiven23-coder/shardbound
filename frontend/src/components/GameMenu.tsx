import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useGame } from '../state/GameContext';
import ActionButton from './ActionButton';
import type { ActionButtonVariant } from './ActionButton';
import ControlsPanel from './ControlsPanel';
import SettingsPanel from './SettingsPanel';

// The pause menu: the current objective (P-46), then Save/Load, Controls,
// Settings, Achievements, Leaderboard, Quit to title. Esc opens and closes it.
// In the portrait control deck it opens upwards.
interface GameMenuProps {
  placement?: 'down' | 'up';
  variant: ActionButtonVariant;
  objective?: string | null;
}

type Panel = 'controls' | 'settings' | null;

export default function GameMenu({ placement = 'down', variant, objective }: GameMenuProps) {
  const { state, dispatch, quit } = useGame();
  const [open, setOpen] = useState(false);
  const [panel, setPanel] = useState<Panel>(null);
  const otherModalOpen = state.modal !== null;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || e.defaultPrevented || otherModalOpen || panel) return;
      setOpen((wasOpen) => !wasOpen);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [otherModalOpen, panel]);

  function pick(action: () => void) {
    setOpen(false);
    action();
  }

  return (
    <div className={`game-menu game-menu-${placement}`}>
      <ActionButton label="Menu" icon="menu" keyHint="Esc" variant={variant} expanded={open} onClick={() => setOpen(!open)} />
      <AnimatePresence>
        {open && (
          <motion.ul
            className="game-menu-list panel"
            initial={{ opacity: 0, y: placement === 'down' ? -6 : 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: placement === 'down' ? -6 : 6 }}
          >
            {objective && (
              <li className="menu-objective">
                <span className="objective-label">Objective</span>
                {objective}
              </li>
            )}
            <MenuItem label="Save / Load" onClick={() => pick(() => dispatch({ type: 'OPEN_MODAL', tab: 'save' }))} />
            <MenuItem label="Controls" onClick={() => pick(() => setPanel('controls'))} />
            <MenuItem label="Settings" onClick={() => pick(() => setPanel('settings'))} />
            <MenuItem label="Achievements" onClick={() => pick(() => dispatch({ type: 'GO', screen: 'achievements' }))} />
            <MenuItem label="Leaderboard" onClick={() => pick(() => dispatch({ type: 'GO', screen: 'leaderboard' }))} />
            <MenuItem label="Quit to title" onClick={() => pick(quit)} />
          </motion.ul>
        )}
      </AnimatePresence>
      {panel === 'controls' && <ControlsPanel onClose={() => setPanel(null)} />}
      {panel === 'settings' && <SettingsPanel onClose={() => setPanel(null)} />}
    </div>
  );
}

interface MenuItemProps {
  label: string;
  onClick: () => void;
}

function MenuItem({ label, onClick }: MenuItemProps) {
  return (
    <li>
      <button type="button" className="btn btn-ghost btn-block" onClick={onClick}>
        {label}
      </button>
    </li>
  );
}

import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useGame } from '../state/GameContext.jsx';

// In-game menu button: Save/Load, Achievements, Leaderboard, Quit to title.
export default function GameMenu() {
  const { dispatch, quit } = useGame();
  const [open, setOpen] = useState(false);

  function pick(action) {
    setOpen(false);
    action();
  }

  return (
    <div className="game-menu">
      <button type="button" className="btn" aria-expanded={open} onClick={() => setOpen(!open)}>
        Menu
      </button>
      <AnimatePresence>
        {open && (
          <motion.ul
            className="game-menu-list panel"
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
          >
            <MenuItem label="Save / Load" onClick={() => pick(() => dispatch({ type: 'OPEN_MODAL', tab: 'save' }))} />
            <MenuItem label="Achievements" onClick={() => pick(() => dispatch({ type: 'GO', screen: 'achievements' }))} />
            <MenuItem label="Leaderboard" onClick={() => pick(() => dispatch({ type: 'GO', screen: 'leaderboard' }))} />
            <MenuItem label="Quit to title" onClick={() => pick(quit)} />
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
}

function MenuItem({ label, onClick }) {
  return (
    <li>
      <button type="button" className="btn btn-ghost btn-block" onClick={onClick}>
        {label}
      </button>
    </li>
  );
}

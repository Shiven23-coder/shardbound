import { motion } from 'framer-motion';
import { useGame } from '../state/GameContext';

// Main menu. "New game" and "Continue/Load" first ask for a profile.
export default function TitleScreen() {
  const { state, dispatch } = useGame();
  const { player, demoMode } = state;

  function openAchievements() {
    if (player) dispatch({ type: 'GO', screen: 'achievements' });
    else dispatch({ type: 'GO_PROFILE', next: 'achievements' });
  }

  return (
    <div className="screen title-screen">
      <motion.h1
        className="game-title"
        initial={{ opacity: 0, letterSpacing: '0.6em' }}
        animate={{ opacity: 1, letterSpacing: '0.18em' }}
        transition={{ duration: 1.2, ease: 'easeOut' }}
      >
        Shardbound
      </motion.h1>
      <p className="tagline">A story of choices, shards and consequences.</p>

      <nav className="title-menu panel" aria-label="Main menu">
        <button type="button" className="btn btn-primary btn-block" onClick={() => dispatch({ type: 'GO_PROFILE', next: 'new' })}>
          New game
        </button>
        <button type="button" className="btn btn-block" onClick={() => dispatch({ type: 'GO_PROFILE', next: 'load' })}>
          Continue / Load
        </button>
        <button type="button" className="btn btn-block" onClick={openAchievements}>
          Achievements
        </button>
        <button type="button" className="btn btn-block" onClick={() => dispatch({ type: 'GO', screen: 'leaderboard' })}>
          Leaderboard
        </button>

        <button
          type="button"
          className="toggle"
          role="switch"
          aria-checked={demoMode}
          onClick={() => dispatch({ type: 'SET_DEMO', demoMode: !demoMode })}
        >
          <span className="toggle-track" aria-hidden="true">
            <span className="toggle-thumb" />
          </span>
          Demo Mode {demoMode ? 'on' : 'off'}
        </button>
      </nav>

      {player && <p className="hint">Playing as {player.username}</p>}
      <p className="hint">Press ~ for the inspector.</p>
    </div>
  );
}

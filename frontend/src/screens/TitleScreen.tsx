import { useState } from 'react';
import { motion } from 'framer-motion';
import { getSessionId } from '../api/client';
import { useGame } from '../state/GameContext';
import ControlsPanel from '../components/ControlsPanel';
import SettingsPanel from '../components/SettingsPanel';

// Main menu. "New game" and "Continue/Load" first ask for a profile.
// "Resume game" appears while this tab still holds a run (e.g. after the
// player left the "Reconnecting…" screen).
export default function TitleScreen() {
  const { state, dispatch, resume } = useGame();
  const { player, demoMode } = state;
  const canResume = Boolean(player && getSessionId());
  const [panel, setPanel] = useState<'controls' | 'settings' | null>(null);

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
        {canResume && (
          <button type="button" className="btn btn-primary btn-block" onClick={resume}>
            Resume game
          </button>
        )}
        <button
          type="button"
          className={canResume ? 'btn btn-block' : 'btn btn-primary btn-block'}
          onClick={() => dispatch({ type: 'GO_PROFILE', next: 'new' })}
        >
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
        <button type="button" className="btn btn-block" onClick={() => setPanel('controls')}>
          Controls
        </button>
        <button type="button" className="btn btn-block" onClick={() => setPanel('settings')}>
          Settings
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
      {import.meta.env.DEV && <p className="hint">Press ~ for the inspector.</p>}
      {panel === 'controls' && <ControlsPanel onClose={() => setPanel(null)} />}
      {panel === 'settings' && <SettingsPanel onClose={() => setPanel(null)} />}
    </div>
  );
}

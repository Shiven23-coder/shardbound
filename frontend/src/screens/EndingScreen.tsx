import { motion } from 'framer-motion';
import { api } from '../api/client';
import { useGame } from '../state/GameContext';
import Typewriter from '../components/Typewriter';
import StatBar from '../components/StatBar';
import { formatSeconds } from '../utils/format';

// Shown when GameView.mode is "ENDED". Everything here comes from GameView.ending.
export default function EndingScreen() {
  const { state, dispatch, run, quit } = useGame();
  const { view, player, demoMode, busy } = state;
  const ending = view?.ending;
  if (!ending) return null; // the view changed while this screen fades out

  function newGame() {
    if (!player) return; // the button is disabled without a profile
    run(() => api.newGame(player.playerId, demoMode));
  }

  return (
    <motion.div
      className="screen ending-screen"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8 }}
    >
      <section className="panel ending-panel" aria-labelledby="ending-title">
        <p className="ending-kicker">An ending</p>
        <h1 id="ending-title" className="ending-title">{ending.title}</h1>
        <Typewriter key={ending.id} text={ending.text} className="story-text" />

        <dl className="ending-stats">
          <div>
            <dt>Time</dt>
            <dd>{formatSeconds(ending.seconds)}</dd>
          </div>
          <div>
            <dt>Level</dt>
            <dd>{view.hud?.level ?? '—'}</dd>
          </div>
        </dl>

        <StatBar
          label={`Endings found ${ending.endingsFound}/${ending.endingsTotal}`}
          value={ending.endingsFound}
          max={ending.endingsTotal}
          kind="progress"
          showNumbers={false}
        />

        <div className="button-row">
          <button type="button" className="btn btn-primary" disabled={busy || !player} onClick={newGame}>
            New game
          </button>
          <button type="button" className="btn" onClick={() => dispatch({ type: 'GO', screen: 'achievements' })}>
            Achievements
          </button>
          <button type="button" className="btn" onClick={() => dispatch({ type: 'GO', screen: 'leaderboard' })}>
            Leaderboard
          </button>
          <button type="button" className="btn btn-ghost" onClick={quit}>
            Title
          </button>
        </div>
      </section>
    </motion.div>
  );
}

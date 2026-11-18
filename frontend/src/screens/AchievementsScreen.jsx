import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { api } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';
import { formatSeconds } from '../utils/format.js';

// Achievements and endings for the current profile, as reported by the server.
export default function AchievementsScreen() {
  const { state, dispatch } = useGame();
  const playerId = state.player?.playerId;
  const [achievements, setAchievements] = useState([]);
  const [endings, setEndings] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (playerId == null) return;
    Promise.all([api.achievements(playerId), api.endings(playerId)])
      .then(([achievementList, endingList]) => {
        setAchievements(achievementList);
        setEndings(endingList);
      })
      .catch((err) => setError(err.message));
  }, [playerId]);

  return (
    <div className="screen list-screen">
      <header className="list-head">
        <h2>Achievements</h2>
        <button type="button" className="btn btn-ghost" onClick={() => dispatch({ type: 'GO', screen: state.returnScreen })}>
          Back
        </button>
      </header>
      {error && <p className="notice notice-error" role="alert">{error}</p>}

      <ul className="achievement-grid">
        {achievements.map((a, i) => (
          <AchievementCard key={a.id} achievement={a} index={i} />
        ))}
      </ul>

      <h2>Endings</h2>
      <ul className="ending-list panel">
        {endings.map((ending) => (
          <li key={ending.id} className={ending.reached ? 'ending-row reached' : 'ending-row'}>
            <span>{ending.reached ? ending.title : '???'}</span>
            <span className="hint">
              {ending.reached ? `Best ${formatSeconds(ending.bestSeconds)}` : 'Not found yet'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// Hidden achievements stay "???" until unlocked.
function AchievementCard({ achievement, index }) {
  const secret = achievement.hidden && !achievement.unlocked;
  return (
    <motion.li
      className={achievement.unlocked ? 'achievement panel unlocked' : 'achievement panel'}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.03 }}
    >
      <strong>{secret ? '???' : achievement.title}</strong>
      <p className="item-desc">{secret ? 'A hidden achievement.' : achievement.description}</p>
      <span className="tag">{achievement.unlocked ? 'Unlocked' : 'Locked'}</span>
    </motion.li>
  );
}

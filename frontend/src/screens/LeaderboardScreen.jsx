import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';
import { formatSeconds } from '../utils/format.js';

// Rows from the v_leaderboard view, already ranked by the server.
export default function LeaderboardScreen() {
  const { state, dispatch } = useGame();
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.leaderboard()
      .then(setRows)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div className="screen list-screen">
      <header className="list-head">
        <h2>Leaderboard</h2>
        <button type="button" className="btn btn-ghost" onClick={() => dispatch({ type: 'GO', screen: state.returnScreen })}>
          Back
        </button>
      </header>
      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {rows === null && !error && <p className="hint">Loading…</p>}
      {rows?.length === 0 && <p className="empty">No one has finished a story yet.</p>}

      {rows?.length > 0 && (
        <div className="table-wrap panel">
          <table className="leaderboard">
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Player</th>
                <th scope="col">Endings</th>
                <th scope="col">Achievements</th>
                <th scope="col">Fastest ending</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={row.username} className={row.username === state.player?.username ? 'me' : undefined}>
                  <td>{i + 1}</td>
                  <td>{row.username}</td>
                  <td>{row.endings}</td>
                  <td>{row.achievements}</td>
                  <td>{formatSeconds(row.fastestEndingSeconds)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

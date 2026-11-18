import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { api, getSessionId } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';

// Debug panel (toggle with ~). Shows the engine's internal state from
// GET /api/debug/inspector so the viva audience can see what Java is doing.
export default function InspectorPanel() {
  const { state, dispatch } = useGame();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  // Refetch when the panel opens and after every new GameView.
  useEffect(() => {
    if (!getSessionId()) {
      setError('No active game session.');
      return;
    }
    api.inspector()
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch((err) => setError(err.message));
  }, [state.viewSeq]);

  return (
    <motion.aside
      className="inspector"
      aria-label="Inspector"
      initial={{ x: -40, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
    >
      <div className="inspector-head">
        <strong>Inspector</strong>
        <button type="button" className="btn btn-ghost btn-small" onClick={() => dispatch({ type: 'TOGGLE_INSPECTOR' })}>
          Close (~)
        </button>
      </div>
      {error && <p className="notice notice-error">{error}</p>}
      {data && <InspectorBody data={data} />}
    </motion.aside>
  );
}

function InspectorBody({ data }) {
  return (
    <div className="inspector-body">
      <dl className="inspector-facts">
        <dt>Node</dt><dd>{data.nodeId}</dd>
        <dt>Mode</dt><dd>{data.mode}</dd>
        <dt>RNG seed</dt><dd>{String(data.rngSeed)}</dd>
      </dl>
      <MapTable title="Flags" map={data.flags} />
      <MapTable title="Reputation" map={data.reputation} />
      <MapTable title="Affinity" map={data.affinity} />
      <h4>Quests (topological order)</h4>
      <ol className="inspector-list">
        {(data.quests ?? []).map((q) => (
          <li key={q.id}>{q.id} — {q.status}</li>
        ))}
      </ol>
      <h4>Recent SQL</h4>
      {(data.recentSql ?? []).map((sql, i) => (
        <pre key={i} className="inspector-sql">{sql}</pre>
      ))}
    </div>
  );
}

// Renders a {key: value} object as a two-column table.
function MapTable({ title, map }) {
  const entries = Object.entries(map ?? {});
  return (
    <>
      <h4>{title}</h4>
      {entries.length === 0 ? (
        <p className="empty">none</p>
      ) : (
        <table className="inspector-table">
          <tbody>
            {entries.map(([key, value]) => (
              <tr key={key}><td>{key}</td><td>{value}</td></tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

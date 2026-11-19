import { useGame } from '../state/GameContext';

// Shown while the server is unreachable (a deploy, a restart, a dropped
// connection). The run is safe on the server (P-22); we keep asking for it
// with growing pauses and continue as soon as it answers (P-30).
export default function ReconnectingOverlay() {
  const { leaveToTitle } = useGame();
  return (
    <div className="reconnecting-backdrop">
      <div className="reconnecting panel" role="status" aria-live="polite">
        <p className="reconnecting-title">Reconnecting…</p>
        <p className="hint">Your game is saved. It will continue as soon as the server answers.</p>
        <button type="button" className="btn" onClick={leaveToTitle}>
          Back to title
        </button>
      </div>
    </div>
  );
}

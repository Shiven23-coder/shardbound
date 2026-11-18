import { useState } from 'react';
import { api } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';

// Asks for a username, creates (or fetches) the profile on the server,
// then continues with whatever the title screen asked for.
export default function ProfileScreen() {
  const { state, dispatch, run } = useGame();
  const [username, setUsername] = useState(state.player?.username ?? '');
  const [error, setError] = useState(null);
  const [working, setWorking] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setWorking(true);
    setError(null);
    try {
      const profile = await api.createProfile(username.trim());
      const player = { playerId: profile.playerId, username: profile.username ?? username.trim() };
      dispatch({ type: 'SET_PLAYER', player });
      await continueWith(player);
    } catch (err) {
      setError(err.message);
    } finally {
      setWorking(false);
    }
  }

  async function continueWith(player) {
    if (state.profileNext === 'new') {
      await run(() => api.newGame(player.playerId, state.demoMode));
    } else if (state.profileNext === 'load') {
      dispatch({ type: 'GO', screen: 'title' });
      dispatch({ type: 'OPEN_MODAL', tab: 'load' });
    } else {
      dispatch({ type: 'GO', screen: 'achievements', returnTo: 'title' });
    }
  }

  return (
    <div className="screen profile-screen">
      <form className="panel profile-form" onSubmit={handleSubmit}>
        <h2>Who is playing?</h2>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          className="input"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          autoFocus
          required
        />
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <div className="button-row">
          <button type="submit" className="btn btn-primary" disabled={working || !username.trim()}>
            {state.profileNext === 'new' ? 'Start' : 'Continue'}
          </button>
          <button type="button" className="btn btn-ghost" onClick={() => dispatch({ type: 'GO', screen: 'title' })}>
            Back
          </button>
        </div>
        {state.profileNext === 'new' && state.demoMode && <p className="hint">Demo Mode is on.</p>}
      </form>
    </div>
  );
}

import { lazy, Suspense, useEffect } from 'react';
import type { ComponentType } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { GameProvider, useGame } from './state/GameContext';
import type { State } from './state/gameReducer';
import TitleScreen from './screens/TitleScreen';
import ProfileScreen from './screens/ProfileScreen';
import WorldScreen from './screens/WorldScreen';
import EndingScreen from './screens/EndingScreen';
import AchievementsScreen from './screens/AchievementsScreen';
import LeaderboardScreen from './screens/LeaderboardScreen';
import ToastLayer from './components/ToastLayer';
import ReconnectingOverlay from './components/ReconnectingOverlay';
import SaveLoadModal from './components/SaveLoadModal';

// Dev builds only: in production the condition is false and the module is never bundled.
const InspectorPanel = import.meta.env.DEV ? lazy(() => import('./dev/InspectorPanel')) : null;

// React and Phaser only render the SessionView sent by Java and post player intents.
export default function App() {
  return (
    <GameProvider>
      <AppShell />
    </GameProvider>
  );
}

// Picks which screen to show. In the game, the world stays on screen until the ending.
// Returns [animation key, screen component].
function screenFor(state: State): [string, ComponentType] {
  const { screen, view } = state;
  if (screen === 'profile') return ['profile', ProfileScreen];
  if (screen === 'achievements') return ['achievements', AchievementsScreen];
  if (screen === 'leaderboard') return ['leaderboard', LeaderboardScreen];
  if (screen === 'game' && view) {
    if (view.mode === 'ENDED') return ['ended', EndingScreen];
    return ['world', WorldScreen];
  }
  return ['title', TitleScreen];
}

function AppShell() {
  const { state, dispatch } = useGame();
  const [key, Screen] = screenFor(state);
  const view = state.view;

  // Region themes are pure CSS driven by this body attribute.
  const regionId = view?.world.regionId;
  useEffect(() => {
    document.body.dataset.region = regionId ?? 'eldmere';
  }, [regionId]);

  // "~" (the backquote key) toggles the dev-only inspector, except while typing.
  // The server only serves /api/debug in its dev profile, and production builds drop this code.
  useEffect(() => {
    if (!import.meta.env.DEV) return;
    function onKey(event: KeyboardEvent) {
      const tagName = event.target instanceof Element ? event.target.tagName : '';
      const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(tagName);
      if (!typing && (event.key === '`' || event.key === '~' || event.code === 'Backquote')) {
        event.preventDefault();
        dispatch({ type: 'TOGGLE_INSPECTOR' });
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [dispatch]);

  return (
    <div className="app">
      {view?.offerReload && state.screen === 'game' && (
        <div className="reload-banner" role="alert">
          <span>You have fallen. Try again from your last save?</span>
          <button type="button" className="btn btn-primary" onClick={() => dispatch({ type: 'OPEN_MODAL', tab: 'load' })}>
            Reload last save
          </button>
        </div>
      )}

      <AnimatePresence mode="wait">
        <motion.main
          key={key}
          className="screen-frame"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
        >
          <Screen />
        </motion.main>
      </AnimatePresence>

      {state.modal && <SaveLoadModal />}
      {InspectorPanel && state.inspectorOpen && (
        <Suspense fallback={null}>
          <InspectorPanel />
        </Suspense>
      )}
      {state.reconnecting && <ReconnectingOverlay />}
      <ToastLayer />
    </div>
  );
}

import { useState } from 'react';
import { api } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';
import Hud from '../components/Hud.jsx';
import StoryPanel from '../components/StoryPanel.jsx';
import GameMenu from '../components/GameMenu.jsx';
import InventoryPanel from '../components/InventoryPanel.jsx';
import QuestLog from '../components/QuestLog.jsx';
import ReputationPanel from '../components/ReputationPanel.jsx';
import Journal from '../components/Journal.jsx';

const TABS = ['Inventory', 'Quests', 'Reputation', 'Journal'];

// Story mode: HUD on top, story in the centre, tabbed side panels.
export default function GameScreen() {
  const { state, run } = useGame();
  const { view, busy } = state;
  if (!view?.node) return null; // the view changed while this screen fades out

  return (
    <div className="screen game-screen">
      <div className="game-top">
        <Hud hud={view.hud} demoMode={view.demoMode} />
        <GameMenu />
      </div>
      <div className="game-body">
        <StoryPanel view={view} busy={busy} onChoose={(id) => run(() => api.choose(id))} />
        <SidePanels
          view={view}
          busy={busy}
          onEquip={(id) => run(() => api.equip(id))}
          onUse={(id) => run(() => api.useItem(id))}
        />
      </div>
    </div>
  );
}

function SidePanels({ view, busy, onEquip, onUse }) {
  const [tab, setTab] = useState(TABS[0]);

  return (
    <aside className="side-panels panel">
      <div className="tabs" role="tablist" aria-label="Character panels">
        {TABS.map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            id={`tab-${name}`}
            aria-selected={tab === name}
            aria-controls="side-tabpanel"
            className={tab === name ? 'tab tab-active' : 'tab'}
            onClick={() => setTab(name)}
          >
            {name}
          </button>
        ))}
      </div>
      <div id="side-tabpanel" role="tabpanel" aria-labelledby={`tab-${tab}`} className="tab-panel">
        {tab === 'Inventory' && (
          <InventoryPanel inventory={view.inventory} busy={busy} onEquip={onEquip} onUse={onUse} />
        )}
        {tab === 'Quests' && <QuestLog quests={view.quests ?? []} />}
        {tab === 'Reputation' && (
          <ReputationPanel reputation={view.reputation ?? []} affinity={view.affinity ?? []} />
        )}
        {tab === 'Journal' && <Journal journal={view.journal ?? []} />}
      </div>
    </aside>
  );
}

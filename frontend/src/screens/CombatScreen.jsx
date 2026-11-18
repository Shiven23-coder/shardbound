import { useEffect, useState } from 'react';
import { useAnimate, useReducedMotion } from 'framer-motion';
import { api } from '../api/client.js';
import { useGame } from '../state/GameContext.jsx';
import Hud from '../components/Hud.jsx';
import EnemyCard from '../components/EnemyCard.jsx';
import CombatActions from '../components/CombatActions.jsx';
import { DamageNumbers } from '../components/DamageNumber.jsx';

const LOG_LINES = 8;

// Turn-based combat. All damage, AI and outcomes are computed in Java;
// this screen animates the results (lastHits) and sends the chosen action.
export default function CombatScreen() {
  const { state } = useGame();
  // While the screen fades out, the view may already be a non-combat one.
  if (!state.view?.combat) return null;
  return <CombatContent />;
}

function CombatContent() {
  const { state, run } = useGame();
  const { view, viewSeq, busy } = state;
  const combat = view.combat;
  const lastHits = combat.lastHits ?? [];
  const [selected, setSelected] = useState(null);
  const [scope, animate] = useAnimate();
  const reduceMotion = useReducedMotion();

  // Target = the enemy the player clicked, or the first living one.
  const living = combat.enemies.filter((enemy) => enemy.alive);
  const selectedIsAlive = living.some((enemy) => enemy.index === selected);
  const target = selectedIsAlive ? selected : (living[0]?.index ?? 0);

  // Shake the whole screen when the newest result contains a critical hit.
  useEffect(() => {
    const hasCrit = (view.combat?.lastHits ?? []).some((hit) => hit.crit);
    if (hasCrit && !reduceMotion && scope.current) {
      animate(scope.current, { x: [0, -12, 12, -8, 8, -4, 0] }, { duration: 0.45 });
    }
  }, [viewSeq]); // run once per new GameView

  function sendAction(type, itemId = null) {
    run(() => api.combatAction(type, target, itemId));
  }

  const hitsOn = (index) => lastHits.filter((hit) => hit.targetIndex === index);

  return (
    <div ref={scope} className="screen combat-screen">
      <div className="hud-wrap">
        <Hud hud={view.hud} demoMode={view.demoMode} />
        <DamageNumbers hits={hitsOn(-1)} seq={viewSeq} />
      </div>

      <section className="combat-arena" aria-labelledby="encounter-title">
        <header className="combat-head">
          <h2 id="encounter-title">{combat.encounterName}</h2>
          <span className="tag">Round {combat.round}</span>
        </header>
        <div className="enemy-row">
          {combat.enemies.map((enemy) => (
            <EnemyCard
              key={enemy.index}
              enemy={enemy}
              hits={hitsOn(enemy.index)}
              seq={viewSeq}
              selected={enemy.index === target && enemy.alive}
              selectable={living.length > 1}
              onSelect={setSelected}
            />
          ))}
        </div>
        {living.length > 1 && <p className="hint">Click an enemy to choose your target.</p>}
        <CombatActions combat={combat} hud={view.hud} busy={busy} onAction={sendAction} />
      </section>

      <section className="combat-log panel" aria-label="Combat log">
        <ol aria-live="polite">
          {combat.log.slice(-LOG_LINES).map((line, i) => (
            <li key={i}>{line}</li>
          ))}
        </ol>
      </section>
    </div>
  );
}

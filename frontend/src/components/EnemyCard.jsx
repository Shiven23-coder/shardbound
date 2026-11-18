import { motion } from 'framer-motion';
import StatBar from './StatBar.jsx';
import { DamageNumbers } from './DamageNumber.jsx';

// One enemy in combat. Clicking a living enemy selects it as the attack target.
// `hits` are this enemy's entries from combat.lastHits; `seq` replays effects.
export default function EnemyCard({ enemy, hits, seq, selected, selectable, onSelect }) {
  const wasHit = hits.length > 0;
  const classes = ['enemy-card', 'panel'];
  if (!enemy.alive) classes.push('enemy-dead');
  if (selected) classes.push('enemy-selected');

  return (
    <button
      type="button"
      className={classes.join(' ')}
      disabled={!enemy.alive}
      aria-pressed={selected}
      onClick={() => selectable && onSelect(enemy.index)}
    >
      {wasHit && (
        <motion.span
          key={seq}
          className="hit-flash"
          initial={{ opacity: 0.85 }}
          animate={{ opacity: 0 }}
          transition={{ duration: 0.45 }}
          aria-hidden="true"
        />
      )}
      <DamageNumbers hits={hits} seq={seq} />
      <span className="enemy-name">
        {enemy.name}
        {enemy.defending && <span className="tag">Defending</span>}
        {!enemy.alive && <span className="tag">Defeated</span>}
      </span>
      <StatBar label="HP" value={enemy.hp} max={enemy.maxHp} kind="enemy" />
      {selected && <span className="enemy-target-label">Target</span>}
    </button>
  );
}

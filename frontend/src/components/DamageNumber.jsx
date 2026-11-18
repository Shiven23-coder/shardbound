import { motion } from 'framer-motion';

// A number that floats up and fades out over a combatant.
// `amount` and `crit` come from combat.lastHits; `offset` spreads several hits apart.
export default function DamageNumber({ amount, crit, offset = 0 }) {
  return (
    <motion.span
      className={crit ? 'damage-number damage-crit' : 'damage-number'}
      style={{ left: `calc(50% + ${offset * 28}px)` }}
      initial={{ y: 0, opacity: 1, scale: crit ? 1.4 : 1 }}
      animate={{ y: -48, opacity: 0, scale: 1 }}
      transition={{ duration: 1.2, ease: 'easeOut' }}
      aria-hidden="true"
    >
      -{amount}
      {crit && <span className="damage-crit-label">CRIT!</span>}
    </motion.span>
  );
}

// Renders all hits for one target. `seq` changes with every new GameView so
// the numbers replay even if the same amount is dealt twice in a row.
export function DamageNumbers({ hits, seq }) {
  return (
    <span className="damage-layer">
      {hits.map((hit, i) => (
        <DamageNumber key={`${seq}-${i}`} amount={hit.amount} crit={hit.crit} offset={i} />
      ))}
    </span>
  );
}

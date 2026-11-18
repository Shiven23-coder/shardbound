import { motion } from 'framer-motion';
import { percent } from '../utils/format.js';

// A labelled bar whose fill animates to value/max. `kind` picks the colour
// (hp, xp, enemy, progress). Values come straight from the GameView.
export default function StatBar({ label, value, max, kind = 'hp', showNumbers = true }) {
  const width = percent(value, max);

  return (
    <div className={`stat-bar stat-bar-${kind}`}>
      <div className="stat-bar-head">
        <span>{label}</span>
        {showNumbers && (
          <span className="stat-bar-numbers">
            {value}/{max}
          </span>
        )}
      </div>
      <div
        className="stat-bar-track"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value}
      >
        <motion.div
          className="stat-bar-fill"
          initial={false}
          animate={{ width: `${width}%` }}
          transition={{ type: 'spring', stiffness: 120, damping: 20 }}
        />
      </div>
    </div>
  );
}

import { motion } from 'framer-motion';
import { percent } from '../utils/format';

// A labelled bar whose fill animates to value/max. `kind` picks the colour
// (hp, xp, progress). Values come straight from the GameView.
/** Colour of the bar fill (see components.css). */
export type StatBarKind = 'hp' | 'xp' | 'progress';

interface StatBarProps {
  label: string;
  value: number;
  max: number;
  kind?: StatBarKind;
  showNumbers?: boolean;
}

export default function StatBar({ label, value, max, kind = 'hp', showNumbers = true }: StatBarProps) {
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

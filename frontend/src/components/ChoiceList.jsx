import { motion } from 'framer-motion';

// Renders the choices exactly as the server sent them. Whether a choice is
// locked (and why) is decided in Java; here we only grey it out.
export default function ChoiceList({ choices = [], onChoose, disabled }) {
  if (choices.length === 0) return null;

  return (
    <ol className="choice-list">
      {choices.map((choice, i) => (
        <motion.li
          key={choice.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 * i }}
        >
          <ChoiceButton choice={choice} onChoose={onChoose} disabled={disabled} />
        </motion.li>
      ))}
    </ol>
  );
}

function ChoiceButton({ choice, onChoose, disabled }) {
  if (choice.locked) {
    const reasonId = `lock-reason-${choice.id}`;
    return (
      <button
        type="button"
        className="choice choice-locked"
        aria-disabled="true"
        aria-describedby={reasonId}
        title={choice.lockReason ?? 'Locked'}
        onClick={(e) => e.preventDefault()}
      >
        <span className="choice-label">
          <LockIcon /> {choice.label}
        </span>
        <small id={reasonId} className="choice-reason">
          {choice.lockReason ?? 'Locked'}
        </small>
      </button>
    );
  }

  return (
    <button
      type="button"
      className="choice"
      disabled={disabled}
      onClick={() => onChoose(choice.id)}
    >
      <span className="choice-label">{choice.label}</span>
    </button>
  );
}

function LockIcon() {
  return (
    <svg className="lock-icon" viewBox="0 0 16 16" width="14" height="14" aria-label="Locked" role="img">
      <path
        fill="currentColor"
        d="M4 7V5a4 4 0 1 1 8 0v2h1a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h1zm2 0h4V5a2 2 0 1 0-4 0v2z"
      />
    </svg>
  );
}

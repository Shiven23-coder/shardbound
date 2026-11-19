import { useEffect, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { useReducedMotion } from 'framer-motion';

const CHARS_PER_TICK = 2;
const TICK_MS = 18;

// Reveals `text` a few characters at a time. Clicking (or Enter/Space)
// shows the whole text at once. Users who prefer reduced motion see it instantly.
// Restarts whenever `text` changes (give it a `key` to force a restart).
interface TypewriterProps {
  text?: string;
  className?: string;
}

export default function Typewriter({ text = '', className = '' }: TypewriterProps) {
  const reduceMotion = useReducedMotion();
  const [shown, setShown] = useState(0);

  useEffect(() => {
    setShown(0);
    if (reduceMotion) return undefined;
    const timer = setInterval(() => {
      setShown((count) => {
        const next = count + CHARS_PER_TICK;
        if (next >= text.length) clearInterval(timer);
        return next;
      });
    }, TICK_MS);
    return () => clearInterval(timer);
  }, [text, reduceMotion]);

  const done = reduceMotion || shown >= text.length;
  const skip = () => setShown(text.length);

  function handleKey(event: KeyboardEvent<HTMLDivElement>) {
    if (!done && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      skip();
    }
  }

  return (
    <div
      className={`typewriter ${className}`}
      onClick={skip}
      onKeyDown={handleKey}
      tabIndex={done ? -1 : 0}
      role="presentation"
      title={done ? undefined : 'Click to show all text'}
    >
      {/* Screen readers get the full text immediately. */}
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {done ? text : text.slice(0, shown)}
        {!done && <span className="typewriter-caret">▍</span>}
      </span>
    </div>
  );
}

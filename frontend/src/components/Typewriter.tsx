import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { useReducedMotion } from 'framer-motion';

const CHARS_PER_TICK = 2;
const TICK_MS = 18;

// Reveals `text` a few characters at a time. Clicking (or Enter/Space)
// shows the whole text at once, and so does `complete` (the dialogue box
// drives its pages this way). Users who prefer reduced motion see it instantly.
// Restarts whenever `text` changes (give it a `key` to force a restart).
interface TypewriterProps {
  text?: string;
  className?: string;
  /** Show the whole text now. */
  complete?: boolean;
  /** Called with the number of characters shown after each step of the reveal (dialogue blips). */
  onProgress?: (shown: number) => void;
  /** Called once when the whole text shows, typed out or skipped. */
  onDone?: () => void;
  /** Characters revealed per tick; Infinity shows the text at once (battle text speed, P-57). */
  charsPerTick?: number;
}

export default function Typewriter({ text = '', className = '', complete = false, onProgress, onDone, charsPerTick = CHARS_PER_TICK }: TypewriterProps) {
  const reduceMotion = useReducedMotion();
  const [shown, setShown] = useState(0);
  const callbacks = useRef({ onProgress, onDone });
  callbacks.current = { onProgress, onDone };
  const instant = !Number.isFinite(charsPerTick);
  const done = reduceMotion || instant || complete || shown >= text.length;
  const finished = useRef(done);
  finished.current = done;

  useEffect(() => {
    setShown(0);
    if (reduceMotion || instant) return undefined;
    let count = 0;
    const timer = setInterval(() => {
      if (finished.current && count > 0) {      // skipped or completed: no more ticks, no more blips
        clearInterval(timer);
        return;
      }
      count += charsPerTick;
      if (count >= text.length) clearInterval(timer);
      setShown((current) => Math.max(current, count));    // a skip is never undone by a later tick
      callbacks.current.onProgress?.(Math.min(count, text.length));
    }, TICK_MS);
    return () => clearInterval(timer);
  }, [text, reduceMotion, instant, charsPerTick]);

  useEffect(() => {
    if (done) callbacks.current.onDone?.();
  }, [done, text]);

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
        {/* The rest, laid out but unseen, so the text takes its full height from the first letter. */}
        {!done && <span className="typewriter-rest">{text.slice(shown)}</span>}
      </span>
    </div>
  );
}

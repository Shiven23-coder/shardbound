import { useCallback, useRef } from 'react';
import Typewriter from './Typewriter';
import { audio, audioTiming } from '../audio/audio';
import { facesetUrl } from '../world/content';
import { useNoBrowserGestures } from '../ui/useNoBrowserGestures';
import type { CutsceneLine as Line } from '../cutscene/useCutscenePlayer';

// A cutscene's line and its Skip button (P-68). The line sits in the dialogue
// box's place (over the world, or in the portrait deck) with the speaker's
// portrait and name; a tap completes it, then moves on. Skip is held: a ring
// fills around it and the cutscene is skipped when it closes.

interface CutsceneLineProps {
  line: Line;
  typed: boolean;
  placement: 'overlay' | 'deck';
  onTyped: () => void;
  onAdvance: () => void;
}

export function CutsceneLine({ line, typed, placement, onTyped, onAdvance }: CutsceneLineProps) {
  const lastBlip = useRef(0);
  const blip = useCallback((shown: number) => {
    if (shown < lastBlip.current) lastBlip.current = 0;
    if (shown - lastBlip.current < audioTiming.blipEveryChars) return;
    lastBlip.current = shown;
    audio.play('blip');
  }, []);
  return (
    <section
      className={`dialogue-box dialogue-${placement} dialogue-message cutscene-line panel`}
      aria-label={line.title || 'Narration'}
      aria-live="polite"
      onClick={onAdvance}
    >
      {line.faceset && <img className="dialogue-portrait" src={facesetUrl(line.faceset)} alt="" />}
      <div className="dialogue-body">
        {line.title && <h2 className="dialogue-title">{line.title}</h2>}
        <div className="dialogue-page">
          <Typewriter key={line.key} text={line.text} className="dialogue-text" complete={typed} onProgress={blip} onDone={onTyped} />
        </div>
        {typed && <span className="dialogue-more cutscene-more" aria-hidden="true">▼</span>}
      </div>
    </section>
  );
}

/** The ring's circumference, in the SVG's units (r = 20). */
const RING = 2 * Math.PI * 20;

interface SkipButtonProps {
  hold: number;
  hinted: boolean;
  /** The key to hold (E, Ⓐ), or none on touch: the Skip button itself is held. */
  keyHint?: string;
  onPress: () => void;
  onRelease: () => void;
}

export function SkipButton({ hold, hinted, keyHint, onPress, onRelease }: SkipButtonProps) {
  const button = useRef<HTMLButtonElement>(null);
  useNoBrowserGestures(button);
  return (
    <div className="cutscene-skip">
      {hinted && <p className="cutscene-hint" role="status">{keyHint ? `Hold ${keyHint} to skip` : 'Hold to skip'}</p>}
      <button
        ref={button}
        type="button"
        className="cutscene-skip-button"
        aria-label="Skip the cutscene (hold)"
        onPointerDown={onPress}
        onPointerUp={onRelease}
        onPointerCancel={onRelease}
        onPointerLeave={onRelease}
        onContextMenu={(event) => event.preventDefault()}
      >
        <svg className="cutscene-ring" viewBox="0 0 48 48" aria-hidden="true">
          <circle className="cutscene-ring-track" cx="24" cy="24" r="20" />
          <circle
            className="cutscene-ring-fill"
            cx="24" cy="24" r="20"
            strokeDasharray={RING}
            strokeDashoffset={RING * (1 - hold)}
            data-progress={hold.toFixed(2)}
          />
        </svg>
        <span className="cutscene-skip-label">Skip</span>
      </button>
    </div>
  );
}

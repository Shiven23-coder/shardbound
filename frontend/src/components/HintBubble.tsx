import { useEffect, useRef, useState } from 'react';
import type { WorldBridge } from '../world/bridge';
import type { InputDevice } from '../input/inputMode';
import { hintText, markHintSeen, seenHints } from '../settings/hints';
import type { HintId } from '../settings/hints';
import { useSettings } from '../settings/settings';

// Contextual hints (P-41): "how to walk" when the world first opens, "how to
// talk or look" the first time something usable is in reach. Each shows once
// per profile: doing the thing or skipping it marks it seen. The Settings
// panel turns them off.

interface Shown {
  hint: HintId;
  talk: boolean;
}

/** The hint to show now, and the function that skips it. */
export function useHint(bridge: WorldBridge, playerId: number | undefined, walking: boolean) {
  const { hints: enabled } = useSettings();
  const [shown, setShown] = useState<Shown | null>(null);
  const seen = useRef<Set<HintId>>(new Set());

  useEffect(() => {
    if (!enabled || playerId === undefined) {
      setShown(null);
      return undefined;
    }
    seen.current = seenHints(playerId);
    const finish = (hint: HintId) => {
      if (!seen.current.has(hint)) {
        seen.current.add(hint);
        markHintSeen(playerId, hint);
      }
      setShown((current) => (current?.hint === hint ? null : current));
    };
    if (!seen.current.has('move')) setShown({ hint: 'move', talk: false });
    const offs = [
      bridge.toReact.on('moved', () => finish('move')),
      bridge.toReact.on('interact', () => finish('interact')),
      bridge.toReact.on('reach', (kind) => {
        if (seen.current.has('interact') || !seen.current.has('move')) return;
        setShown(kind ? { hint: 'interact', talk: kind === 'npc' } : null);
      }),
    ];
    return () => offs.forEach((off) => off());
  }, [bridge, playerId, enabled]);

  const skip = () => {
    if (shown && playerId !== undefined) {
      seen.current.add(shown.hint);
      markHintSeen(playerId, shown.hint);
    }
    setShown(null);
  };
  return { shown: walking ? shown : null, skip };
}

interface HintBubbleProps {
  hint: HintId;
  talk: boolean;
  device: InputDevice;
  onSkip: () => void;
}

export default function HintBubble({ hint, talk, device, onSkip }: HintBubbleProps) {
  return (
    <div className="hint-bubble" role="status">
      <span>{hintText(hint, device, talk)}</span>
      <button type="button" className="btn btn-ghost hint-skip" aria-label="Skip this hint" onClick={onSkip}>✕</button>
    </div>
  );
}

import { useEffect, useRef, useState } from 'react';

// The Shards counter (V2_SPEC §1, P-61): bottom-right on desktop and upright,
// under the top-right buttons sideways (Ⓐ owns the bottom-right corner there).
// It pulses when the amount changes: a victory, a level bought, Shards lost or
// recovered.
export default function ShardsCounter({ shards }: { shards: number }) {
  const previous = useRef(shards);
  const [pulse, setPulse] = useState(0);
  useEffect(() => {
    if (previous.current !== shards) setPulse((n) => n + 1);
    previous.current = shards;
  }, [shards]);
  return (
    <p className="shards-counter" data-testid="hud-shards" aria-label={`${shards} Shards`}>
      <span aria-hidden="true">◆</span>
      <span key={pulse} className={pulse > 0 ? 'shards-amount shards-changed' : 'shards-amount'}>{shards}</span>
    </p>
  );
}

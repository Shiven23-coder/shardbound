import { useEffect, useRef } from 'react';
import type { WorldBridge } from './bridge';

// Hosts the Phaser world. Phaser is fetched lazily the first time a world is
// shown, and the game is destroyed when this component unmounts.
interface WorldCanvasProps {
  bridge: WorldBridge;
}

export default function WorldCanvas({ bridge }: WorldCanvasProps) {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let stop: (() => void) | undefined;
    let cancelled = false;
    void import('./startWorld').then(({ startWorld }) => {
      if (!cancelled && host.current) stop = startWorld(host.current, bridge);
    });
    return () => {
      cancelled = true;
      stop?.();
    };
  }, [bridge]);

  return <div ref={host} className="world-canvas" data-testid="world-canvas" />;
}

import { useEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import type { Direction, WorldBridge } from '../world/bridge';
import { useNoBrowserGestures } from '../ui/useNoBrowserGestures';

// On-screen controls for touch (P-40): a D-pad whose every press is one step
// (or a turn) and that keeps walking while held (P-51), and the interact button (Ⓐ). Phone landscape lays them over the
// game view, translucent until pressed; phone portrait puts them in the control
// deck. Touch pointers stay captured by the button they pressed until lifted.
interface ControlProps {
  bridge: WorldBridge;
}

const PAD: { direction: Direction; label: string; symbol: string }[] = [
  { direction: 'UP', label: 'Walk up', symbol: '▲' },
  { direction: 'LEFT', label: 'Walk left', symbol: '◀' },
  { direction: 'RIGHT', label: 'Walk right', symbol: '▶' },
  { direction: 'DOWN', label: 'Walk down', symbol: '▼' },
];

export function Dpad({ bridge }: ControlProps) {
  const [held, setHeld] = useState<Direction | null>(null);
  /** The finger holding a direction. */
  const pointer = useRef<number | null>(null);
  const pad = useRef<HTMLDivElement>(null);
  useNoBrowserGestures(pad);

  function hold(direction: Direction) {
    return (event: ReactPointerEvent) => {
      pointer.current = event.pointerId;
      bridge.direction = direction;
      bridge.toScene.emit('press', direction);
      setHeld(direction);
    };
  }

  function release() {
    pointer.current = null;
    bridge.direction = null;
    setHeld(null);
  }

  // The finger may lift anywhere: the button may be gone (a dialogue takes the D-pad's place in
  // the portrait deck) or covered. Any lift of that finger, or losing the page, stops the walk.
  useEffect(() => {
    if (!held) return undefined;
    const lifted = (event: PointerEvent) => {
      if (event.pointerId === pointer.current) release();
    };
    window.addEventListener('pointerup', lifted);
    window.addEventListener('pointercancel', lifted);
    window.addEventListener('blur', release);
    return () => {
      window.removeEventListener('pointerup', lifted);
      window.removeEventListener('pointercancel', lifted);
      window.removeEventListener('blur', release);
    };
  }, [held]);

  // Unmounted while held: nobody can lift that finger off it any more.
  useEffect(() => () => {
    bridge.direction = null;
  }, [bridge]);

  return (
    <div ref={pad} className={held ? 'dpad pressed' : 'dpad'} role="group" aria-label="Walk">
      {PAD.map(({ direction, label, symbol }) => (
        <button
          key={direction}
          type="button"
          className={`dpad-button dpad-${direction.toLowerCase()}`}
          aria-label={label}
          onPointerDown={hold(direction)}
          onPointerUp={release}
          onPointerCancel={release}
          onPointerLeave={release}
          onContextMenu={(event) => event.preventDefault()}
        >
          {symbol}
        </button>
      ))}
    </div>
  );
}

export function InteractButton({ bridge }: ControlProps) {
  const [pressed, setPressed] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  useNoBrowserGestures(button);

  return (
    <button
      ref={button}
      type="button"
      className={pressed ? 'interact-button pressed' : 'interact-button'}
      aria-label="Talk or use"
      onPointerDown={() => {
        setPressed(true);
        bridge.toScene.emit('interact', undefined);
      }}
      onPointerUp={() => setPressed(false)}
      onPointerCancel={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      onContextMenu={(event) => event.preventDefault()}
    >
      A
    </button>
  );
}

/** Phone landscape: the controls float over the bottom corners of the game view. */
export default function TouchOverlay({ bridge }: ControlProps) {
  return (
    <div className="touch-controls">
      <Dpad bridge={bridge} />
      <InteractButton bridge={bridge} />
    </div>
  );
}

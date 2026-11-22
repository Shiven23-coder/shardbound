import { forwardRef, useEffect, useRef, useState } from 'react';
import type { HudView } from '../api/types';
import { changed, LOW_HP, mayFade, NUMBERS_MS, wakes } from './hudActivity';

// The compact HUD (P-39): a level badge, a thin HP bar and a thinner energy
// bar in the top-left corner, no panel. Desktop always shows the numbers;
// phones show them while a value changes or after a tap. At full HP it fades
// after a few calm seconds and wakes on damage, energy use or a level-up.
// When the player walks under it, it all but disappears.
interface HudClusterProps {
  hud: HudView;
  /** Numbers always (desktop) or only while changing / after a tap (phones). */
  alwaysNumbers: boolean;
  /** A creep is close (from V4). */
  enemyNear?: boolean;
  /** The player is under the cluster: get out of the way. */
  dodge?: boolean;
}

const HudCluster = forwardRef<HTMLButtonElement, HudClusterProps>(function HudCluster(
  { hud, alwaysNumbers, enemyNear = false, dodge = false }, ref,
) {
  const previous = useRef<HudView | null>(null);
  const [wokeAt, setWokeAt] = useState(() => Date.now());
  const [numbersUntil, setNumbersUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (wakes(previous.current, hud)) setWokeAt(Date.now());
    if (changed(previous.current, hud)) setNumbersUntil(Date.now() + NUMBERS_MS);
    previous.current = hud;
  }, [hud]);

  // A slow clock is enough to fade and to hide the numbers again.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(timer);
  }, []);

  const faded = mayFade(hud, now - wokeAt, enemyNear);
  const numbers = alwaysNumbers || now < numbersUntil;
  const hpShare = hud.maxHp > 0 ? hud.hp / hud.maxHp : 0;
  const energyShare = hud.maxEnergy > 0 ? hud.energy / hud.maxEnergy : 0;

  const classes = ['hud-cluster'];
  if (faded) classes.push('hud-faded');
  if (dodge) classes.push('hud-dodge');
  if (hpShare < LOW_HP) classes.push('hud-low');

  return (
    <button
      ref={ref}
      type="button"
      className={classes.join(' ')}
      aria-label={`Level ${hud.level}, HP ${hud.hp} of ${hud.maxHp}, energy ${hud.energy} of ${hud.maxEnergy}`}
      onClick={() => {
        setWokeAt(Date.now());
        setNumbersUntil(Date.now() + NUMBERS_MS);
      }}
    >
      <span className="hud-body" aria-hidden="true">
        <span className="hud-level">{hud.level}</span>
        <span className="hud-meters">
          <span className="hud-meter hud-hp"><span style={{ width: `${hpShare * 100}%` }} /></span>
          <span className="hud-meter hud-energy"><span style={{ width: `${energyShare * 100}%` }} /></span>
          {numbers && (
            <span className="hud-numbers">
              {hud.hp}/{hud.maxHp} · EN {hud.energy}/{hud.maxEnergy}
            </span>
          )}
        </span>
      </span>
    </button>
  );
});

export default HudCluster;

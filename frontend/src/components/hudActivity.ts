import type { HudView } from '../api/types';

// When the compact HUD (P-39) wakes up to full opacity and when it may fade.

/** Below this share of HP the HUD stays fully visible. */
export const LOW_HP = 0.3;
/** Calm time at full HP before the HUD fades. */
export const CALM_MS = 4000;
/** How long changed numbers stay visible on phones. */
export const NUMBERS_MS = 2500;

/** True when the change from `before` to `after` is something the player should see: damage, FP use, level-up. */
export function wakes(before: HudView | null, after: HudView): boolean {
  if (!before) return false;
  return after.hp < before.hp || after.fp < before.fp || after.level > before.level;
}

/** True when any number shown in the cluster changed. */
export function changed(before: HudView | null, after: HudView): boolean {
  if (!before) return false;
  return after.hp !== before.hp || after.maxHp !== before.maxHp || after.fp !== before.fp
    || after.level !== before.level;
}

/** The HUD may fade only at full HP, when nothing woke it for a while and no enemy is near. */
export function mayFade(hud: HudView, sinceWakeMs: number, enemyNear: boolean): boolean {
  return hud.hp >= hud.maxHp && sinceWakeMs >= CALM_MS && !enemyNear;
}

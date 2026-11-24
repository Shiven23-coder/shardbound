// Cutscenes (P-68), the parts with no Phaser and no React: which side plays a
// step, who speaks a line, and how far a hold to skip has come.
import { actorArt, bossArt } from '../world/content';
import type { CutsceneStepView, CutsceneView } from '../api/types';

/**
 * Who plays a step: React shows `say` lines in the dialogue box and starts
 * music and sounds; the world scene stages everything else and says when it is done.
 */
export type StepRole = 'say' | 'audio' | 'scene';

export function stepRole(step: CutsceneStepView): StepRole {
  if (step.kind === 'SAY') return 'say';
  return step.kind === 'MUSIC' || step.kind === 'SOUND' ? 'audio' : 'scene';
}

/** How long E, Ⓐ or the Skip button must be held to skip (P-68). */
export const HOLD_TO_SKIP_MS = 1000;

/** How far a hold to skip has come, 0 to 1: nothing held is 0. */
export function holdProgress(startedAt: number | null, now: number): number {
  if (startedAt === null) return 0;
  return Math.max(0, Math.min(1, (now - startedAt) / HOLD_TO_SKIP_MS));
}

export interface Speaker {
  title: string;
  /** The portrait's path inside content/art, or undefined for narration. */
  faceset?: string;
}

/** Who says a line: a cutscene actor (its name and portrait), an NPC (its portrait), or nobody (narration). */
export function speakerOf(cutscene: CutsceneView, step: CutsceneStepView): Speaker {
  if (!step.speaker) return { title: '' };
  const actor = cutscene.actors.find((a) => a.id === step.speaker);
  if (actor) {
    return { title: actor.name ?? '', faceset: bossArt(actor.sprite)?.faceset ?? actorArt(actor.sprite)?.faceset };
  }
  return { title: '', faceset: actorArt(`npc.${step.speaker}`)?.faceset };
}

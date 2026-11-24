import { describe, expect, it } from 'vitest';
import { HOLD_TO_SKIP_MS, holdProgress, speakerOf, stepRole } from './cutscene';
import type { CutsceneStepView, CutsceneView } from '../api/types';

const step = (kind: string, extra: Partial<CutsceneStepView> = {}): CutsceneStepView => ({ kind, path: [], ...extra });
const intro: CutsceneView = {
  id: 'herald_intro',
  actors: [{ id: 'herald', sprite: 'boss.ashen_herald', x: 10, y: 1, facing: 'DOWN', name: 'The Ashen Herald' }],
  steps: [],
};

describe('cutscene steps', () => {
  it('leaves lines to the dialogue box, music and sounds to React, and the rest to the scene', () => {
    expect(stepRole(step('SAY'))).toBe('say');
    expect(stepRole(step('MUSIC'))).toBe('audio');
    expect(stepRole(step('SOUND'))).toBe('audio');
    for (const kind of ['MOVE', 'FACE', 'CAMERA', 'FADE', 'FLASH', 'WAIT', 'LIGHT', 'VANISH']) {
      expect(stepRole(step(kind))).toBe('scene');
    }
  });

  it("titles an actor's line with its name and portrait, and leaves narration bare", () => {
    expect(speakerOf(intro, step('SAY', { speaker: 'herald' }))).toEqual({
      title: 'The Ashen Herald', faceset: 'ninja-adventure/eldmere/bosses/TenguRed/Faceset.png',
    });
    expect(speakerOf(intro, step('SAY'))).toEqual({ title: '' });
  });
});

describe('hold to skip', () => {
  it('fills over about a second and stays full', () => {
    expect(holdProgress(null, 5000)).toBe(0);
    expect(holdProgress(1000, 1000 + HOLD_TO_SKIP_MS / 2)).toBeCloseTo(0.5);
    expect(holdProgress(1000, 1000 + HOLD_TO_SKIP_MS * 3)).toBe(1);
    expect(HOLD_TO_SKIP_MS).toBe(1000);
  });
});

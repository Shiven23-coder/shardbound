import { describe, expect, it } from 'vitest';
import grades from '../../../content/art/grades.json';
import { lightingFor, type RegionLighting } from './content';

/**
 * The darkest the ambient light may multiply any colour channel by: below this the
 * storm night was unreadable on a phone at half brightness (real-device check, P-52).
 */
const READABLE_AT_HALF_BRIGHTNESS = 0.55;

describe('lighting stays readable on a phone (P-52)', () => {
  const regions = grades.lighting as Record<string, RegionLighting>;
  for (const [regionId, region] of Object.entries(regions)) {
    for (const stateId of Object.keys(region.states)) {
      it(`${regionId} ${stateId}: every channel of the ambient light is at least ${READABLE_AT_HALF_BRIGHTNESS}`, () => {
        const { state } = lightingFor(regionId, stateId);
        for (const channel of state.tint) {
          expect(Math.min(1, state.ambient * channel)).toBeGreaterThanOrEqual(READABLE_AT_HALF_BRIGHTNESS);
        }
      });
    }
  }
});

import { expect, test, type Page } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as backend from '../support/backend';
import { choose, inArea, newGame, toTheRuinsLanternAfterTheAshFalls, walkTo, walkToTile } from '../support/world';

// The mobile performance budget (§4.13, P-24, P-38): with the CPU slowed down
// as on a mid-range phone, walking every Eldmere area keeps the main-thread
// CPU time of each frame and the JS heap within budget, and so does walking
// through the falling ash of the Old Ruins and the Ash Rise (P-67). A frame's
// CPU time is the sum of the animation-frame callbacks Chrome runs for it
// (Phaser's update and render), read from a DevTools trace. GPU cost is left to
// the real-device check: CI browsers draw WebGL in software.
//
// Battle start (P-70) runs from the encounter's answer to the battle on screen,
// on one cold battle (the first after the page loads, the one players feel) and
// seven warm ones. Its budgets, cold and warm p95, are for Chrome at 4x CPU
// throttle on the reference machine, a desktop with a hardware GPU: the run
// before a release. CI's runners draw WebGL in software, two to four times
// slower and noisier, so on CI (CI set) battle start is held to the last good
// nightly's numbers instead.
//
// Nightly and before releases only (P-36): set PERF=1 to run it. Each run
// writes its numbers to e2e/perf/perf.json (the nightly keeps it as an
// artifact). When the last good nightly's file is at PERF_BASELINE, the run
// also fails if a number is more than budgets.performance.maxRegression worse.

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const budget = JSON.parse(readFileSync(path.join(ROOT, 'content', 'balance.json'), 'utf8')).budgets.performance;
const RESULT = path.join(ROOT, 'e2e', 'perf', 'perf.json');
const BASELINE = process.env.PERF_BASELINE ?? path.join(ROOT, 'e2e', 'perf', 'baseline', 'perf.json');

/** The numbers one run stores for the next one to compare with; each test adds its own. */
interface PerfNumbers {
  p95FrameCpuMs?: number; jsHeapMb?: number; frames?: number;
  battleStartColdMs?: number; battleStartWarmMedianMs?: number; battleStartWarmP95Ms?: number;
  commit: string | null; date: string;
}

/** On CI the absolute battle start budgets give way to the last green nightly's numbers (§4.13). */
const ON_CI = Boolean(process.env.CI);

/** This run's numbers so far, with `numbers` added, written back for the nightly to keep. */
function record(numbers: Partial<PerfNumbers>): void {
  const sofar: PerfNumbers = existsSync(RESULT) ? JSON.parse(readFileSync(RESULT, 'utf8')) : { commit: null, date: '' };
  mkdirSync(path.dirname(RESULT), { recursive: true });
  writeFileSync(RESULT, `${JSON.stringify({ ...sofar, ...numbers, commit: process.env.GITHUB_SHA ?? null, date: new Date().toISOString() }, null, 2)}\n`);
}

function baseline(): PerfNumbers | null {
  return existsSync(BASELINE) ? JSON.parse(readFileSync(BASELINE, 'utf8')) : null;
}

/** The value at the 95th percentile by nearest rank. */
function percentile95(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.ceil(sorted.length * 0.95) - 1]!;
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)]!;
}

interface TraceEvent { name: string; ts: number; dur?: number; tid: number }

/** Sums back-to-back animation-frame callbacks into one CPU time per frame, in milliseconds. */
function frameCpuTimes(events: TraceEvent[]): number[] {
  const callbacks = events.filter((e) => e.name === 'FireAnimationFrame' && e.dur !== undefined)
    .sort((a, b) => a.ts - b.ts);
  const frames: number[] = [];
  let frameEnd = -Infinity;
  for (const event of callbacks) {
    if (event.ts > frameEnd + 1_000) frames.push(0);           // more than 1 ms after the last one: a new frame
    frames[frames.length - 1]! += event.dur! / 1000;
    frameEnd = event.ts + event.dur!;
  }
  return frames;
}

test.skip(!process.env.PERF, 'performance budget runs nightly (PERF=1)');

test.beforeAll(async () => {
  rmSync(RESULT, { force: true });                                  // this run's numbers only
  await backend.start();
});

test.afterAll(async () => {
  await backend.stop();
});

test('walking every Eldmere area stays within the frame CPU and heap budgets', async ({ page }) => {
  await newGame(page, `perf-${Date.now() % 1e9}`);
  await choose(page, 'Ignore the shouting and follow the light');   // storm night: rain and lights, the heaviest scene
  await inArea(page, 'Cottage Lane');

  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Performance.enable');
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: budget.cpuThrottle });
  const events: TraceEvent[] = [];
  cdp.on('Tracing.dataCollected', ({ value }) => events.push(...(value as TraceEvent[])));
  await cdp.send('Tracing.start', { categories: 'devtools.timeline', transferMode: 'ReportEvents' });

  for (const [exit, next] of [['to_ruins', 'The Old Ruins'], ['to_lane', 'Cottage Lane'], ['to_square', 'Eldmere Square'],
    ['to_mill', 'Mill Road'], ['to_square', 'Eldmere Square']] as const) {
    await walkTo(page, 'keyboard', exit);
    await inArea(page, next);
  }

  const done = new Promise((resolve) => cdp.once('Tracing.tracingComplete', resolve));
  await cdp.send('Tracing.end');
  await done;
  const frames = frameCpuTimes(events).sort((a, b) => a - b);
  expect(frames.length, 'frames traced').toBeGreaterThan(300);
  const p95 = frames[Math.floor(frames.length * 0.95)]!;
  const metrics = (await cdp.send('Performance.getMetrics')).metrics;
  const heapMb = (metrics.find((m) => m.name === 'JSHeapUsedSize')?.value ?? Infinity) / (1024 * 1024);
  record({ p95FrameCpuMs: Number(p95.toFixed(2)), jsHeapMb: Number(heapMb.toFixed(1)), frames: frames.length });
  const last = baseline();
  test.info().annotations.push({
    type: 'perf',
    description: `${frames.length} frames, p95 CPU ${p95.toFixed(1)} ms per frame, heap ${heapMb.toFixed(1)} MB; `
      + (last?.p95FrameCpuMs ? `last good nightly p95 ${last.p95FrameCpuMs} ms (${last.commit ?? 'unknown commit'})` : 'no baseline'),
  });

  expect(p95, '95th-percentile CPU time per frame (ms)').toBeLessThanOrEqual(budget.p95FrameCpuMs);
  expect(heapMb, 'JS heap (MB)').toBeLessThanOrEqual(budget.jsHeapMb);
  if (last?.p95FrameCpuMs) {
    expect(p95, `p95 CPU time per frame against the last good nightly (${last.p95FrameCpuMs} ms)`)
      .toBeLessThanOrEqual(last.p95FrameCpuMs * (1 + budget.maxRegression));
  }
});

/** The Mire Slime's spot in the meadow (aggro 1) and a tile well out of its reach: past aggro + flee.ignoreTiles. */
const SLIME = 'meadow_creep_b';
const AWAY_FROM_THE_SLIME: [number, number] = [7, 10];
const WARM_BATTLES = 7;

/** Flees the battle on screen through its menu, trying again until it works (the slime is slower: 60 %). */
async function flee(page: Page): Promise<void> {
  const box = page.locator('.battle-box');
  for (let step = 0; step < 300 && (await box.isVisible()); step++) {
    const continueButton = box.getByRole('button', { name: 'Continue' });
    const menu = box.locator('.battle-menu');
    if (await continueButton.isVisible()) {
      await continueButton.click();
    } else if (await menu.isVisible()) {
      const label = await menu.getAttribute('aria-label');
      await menu.getByRole('button', { name: label === 'Battle menu' ? /^Flee/ : /^Back/ }).click();
    } else {
      await box.locator('.battle-text').click();                       // skip the round's text
    }
    await page.waitForTimeout(60);
  }
  await expect(box).toBeHidden();
}

test('battle start: one cold battle and seven warm ones stay within budget', async ({ page }) => {
  test.setTimeout(600_000);
  await newGame(page, `perfb-${Date.now() % 1e9}`);
  await choose(page, 'Ignore the shouting and follow the light');
  await inArea(page, 'Cottage Lane');
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: budget.cpuThrottle });
  // In the page: each time a battle box appears (it may come and go within a battle); each encounter's answer is in
  // the resource timings.
  await page.evaluate(() => {
    performance.setResourceTimingBufferSize(2000);
    const w = window as unknown as { battlesShownAt: number[] };
    w.battlesShownAt = [];
    let showing = false;
    new MutationObserver(() => {
      const now = document.querySelector('.battle-box') !== null;
      if (now && !showing) w.battlesShownAt.push(performance.now());
      showing = now;
    }).observe(document.body, { childList: true, subtree: true });
  });

  for (let battle = 0; battle <= WARM_BATTLES; battle++) {
    await walkTo(page, 'keyboard', SLIME);
    await expect(page.locator('.battle-box .battle-menu')).toBeVisible({ timeout: 30_000 });
    await flee(page);
    await walkToTile(page, 'keyboard', AWAY_FROM_THE_SLIME);           // out of reach: the next approach starts a battle
  }
  const starts = await page.evaluate(() => {
    const answers = performance.getEntriesByType('resource')
      .filter((e) => e.name.includes('/api/world/encounter')) as PerformanceResourceTiming[];
    const shown = (window as unknown as { battlesShownAt: number[] }).battlesShownAt;
    // Each encounter's battle: the first time a battle box showed after its answer.
    return answers.map((answer) => (shown.find((at) => at >= answer.responseEnd) ?? Infinity) - answer.responseEnd);
  });
  expect(starts.length, 'battles measured').toBe(WARM_BATTLES + 1);
  expect(starts.every(Number.isFinite), `every battle showed: ${starts.join(', ')}`).toBe(true);
  const cold = starts[0]!;
  const warm = starts.slice(1);
  const numbers = {
    battleStartColdMs: Math.round(cold),
    battleStartWarmMedianMs: Math.round(median(warm)),
    battleStartWarmP95Ms: Math.round(percentile95(warm)),
  };
  record(numbers);
  const last = baseline();
  test.info().annotations.push({
    type: 'perf',
    description: `battle start: cold ${numbers.battleStartColdMs} ms, warm median ${numbers.battleStartWarmMedianMs} ms, `
      + `warm p95 ${numbers.battleStartWarmP95Ms} ms (all: ${starts.map((ms) => Math.round(ms)).join(', ')}); `
      + (last?.battleStartColdMs ? `last good nightly cold ${last.battleStartColdMs} ms, warm p95 ${last.battleStartWarmP95Ms} ms` : 'no baseline'),
  });

  if (!ON_CI) {
    expect(numbers.battleStartColdMs, 'cold battle start (ms)').toBeLessThanOrEqual(budget.battleStartColdMs);
    expect(numbers.battleStartWarmP95Ms, 'warm battle start p95 (ms)').toBeLessThanOrEqual(budget.battleStartWarmP95Ms);
  } else if (last?.battleStartColdMs && last.battleStartWarmP95Ms) {
    expect(numbers.battleStartColdMs, `cold battle start against the last good nightly (${last.battleStartColdMs} ms)`)
      .toBeLessThanOrEqual(last.battleStartColdMs * (1 + budget.maxRegression));
    expect(numbers.battleStartWarmP95Ms, `warm battle start p95 against the last good nightly (${last.battleStartWarmP95Ms} ms)`)
      .toBeLessThanOrEqual(last.battleStartWarmP95Ms * (1 + budget.maxRegression));
  }
});

test('walking through the falling ash stays within the frame CPU budget (P-67)', async ({ page }) => {
  test.setTimeout(480_000);
  await toTheRuinsLanternAfterTheAshFalls(page, 'keyboard', `perfa-${Date.now() % 1e9}`);   // the ash falls from here on
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: budget.cpuThrottle });
  const events: TraceEvent[] = [];
  cdp.on('Tracing.dataCollected', ({ value }) => events.push(...(value as TraceEvent[])));
  await cdp.send('Tracing.start', { categories: 'devtools.timeline', transferMode: 'ReportEvents' });

  // Up onto the Rise and back, twice: enough frames for a p95 even on a slow runner.
  for (let lap = 0; lap < 2; lap++) {
    await walkTo(page, 'keyboard', 'arena_way');
    await inArea(page, 'The Ash Rise');
    await walkToTile(page, 'keyboard', [10, 8]);                     // across the Rise, short of the Herald's trigger
    await walkTo(page, 'keyboard', 'to_ruins');
    await inArea(page, 'The Old Ruins');
  }

  const done = new Promise((resolve) => cdp.once('Tracing.tracingComplete', resolve));
  await cdp.send('Tracing.end');
  await done;
  const frames = frameCpuTimes(events).sort((a, b) => a - b);
  expect(frames.length, 'frames traced').toBeGreaterThan(150);
  const p95 = frames[Math.floor(frames.length * 0.95)]!;
  test.info().annotations.push({ type: 'perf', description: `ash: ${frames.length} frames, p95 CPU ${p95.toFixed(1)} ms per frame` });
  expect(p95, '95th-percentile CPU time per frame in the ash (ms)').toBeLessThanOrEqual(budget.p95FrameCpuMs);
});

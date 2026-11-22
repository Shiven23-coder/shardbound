import { expect, test } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as backend from '../support/backend';
import { choose, inArea, newGame, walkTo } from '../support/world';

// The mobile performance budget (§4.13, P-24, P-38): with the CPU slowed down
// as on a mid-range phone, walking every Eldmere area keeps the main-thread
// CPU time of each frame and the JS heap within budget. A frame's CPU time is
// the sum of the animation-frame callbacks Chrome runs for it (Phaser's update
// and render), read from a DevTools trace. GPU cost is left to the real-device
// check: CI browsers draw WebGL in software. Nightly and before releases only
// (P-36): set PERF=1 to run it.
//
// Each run writes its numbers to e2e/perf/perf.json (the nightly keeps it as
// an artifact). When the last good nightly's file is at PERF_BASELINE, the run
// also fails if its p95 is more than budgets.performance.maxRegression worse.

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const budget = JSON.parse(readFileSync(path.join(ROOT, 'content', 'balance.json'), 'utf8')).budgets.performance;
const RESULT = path.join(ROOT, 'e2e', 'perf', 'perf.json');
const BASELINE = process.env.PERF_BASELINE ?? path.join(ROOT, 'e2e', 'perf', 'baseline', 'perf.json');

/** The numbers one run stores for the next one to compare with. */
interface PerfNumbers { p95FrameCpuMs: number; jsHeapMb: number; frames: number; commit: string | null; date: string }

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
  const numbers: PerfNumbers = {
    p95FrameCpuMs: Number(p95.toFixed(2)),
    jsHeapMb: Number(heapMb.toFixed(1)),
    frames: frames.length,
    commit: process.env.GITHUB_SHA ?? null,
    date: new Date().toISOString(),
  };
  mkdirSync(path.dirname(RESULT), { recursive: true });
  writeFileSync(RESULT, `${JSON.stringify(numbers, null, 2)}\n`);
  const baseline: PerfNumbers | null = existsSync(BASELINE) ? JSON.parse(readFileSync(BASELINE, 'utf8')) : null;
  test.info().annotations.push({
    type: 'perf',
    description: `${frames.length} frames, p95 CPU ${p95.toFixed(1)} ms per frame, heap ${heapMb.toFixed(1)} MB; `
      + (baseline ? `last good nightly p95 ${baseline.p95FrameCpuMs} ms (${baseline.commit ?? 'unknown commit'})` : 'no baseline'),
  });

  expect(p95, '95th-percentile CPU time per frame (ms)').toBeLessThanOrEqual(budget.p95FrameCpuMs);
  expect(heapMb, 'JS heap (MB)').toBeLessThanOrEqual(budget.jsHeapMb);
  if (baseline) {
    expect(p95, `p95 CPU time per frame against the last good nightly (${baseline.p95FrameCpuMs} ms)`)
      .toBeLessThanOrEqual(baseline.p95FrameCpuMs * (1 + budget.maxRegression));
  }
});

test('a battle is on screen within the battle start budget of the encounter', async ({ page }) => {
  await newGame(page, `perfb-${Date.now() % 1e9}`);
  await choose(page, 'Ignore the shouting and follow the light');
  await inArea(page, 'Cottage Lane');
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: budget.cpuThrottle });
  // In the page: when the encounter's answer arrived, and when the battle box first showed.
  await page.evaluate(() => {
    const w = window as unknown as { battleShownAt?: number };
    new MutationObserver((_, observer) => {
      if (document.querySelector('.battle-box')) {
        w.battleShownAt = performance.now();
        observer.disconnect();
      }
    }).observe(document.body, { childList: true, subtree: true });
  });

  await walkTo(page, 'keyboard', 'meadow_creep_b');
  await expect(page.locator('.battle-box .battle-menu')).toBeVisible({ timeout: 30_000 });
  const startMs = await page.evaluate(() => {
    const answer = performance.getEntriesByType('resource')
      .filter((e) => e.name.includes('/api/world/encounter'))
      .at(-1) as PerformanceResourceTiming | undefined;
    const shown = (window as unknown as { battleShownAt?: number }).battleShownAt;
    return answer && shown ? shown - answer.responseEnd : Infinity;
  });
  test.info().annotations.push({ type: 'perf', description: `battle on screen ${startMs.toFixed(0)} ms after the encounter's answer` });
  expect(startMs, 'battle start (ms)').toBeLessThanOrEqual(budget.battleStartMs);
});

// Colour-grades source art for a region (P-29): the bright Ninja Adventure
// palette becomes Shardbound's dark fantasy look. Deterministic and offline,
// so every graded file can be regenerated and is recorded in CREDITS.md.
//
//   node tools/art/grade.mjs <region> <source.png> <out.png>
//
// Grade settings per region (with per-file overrides) live in content/art/grades.json.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readPng, writePng } from '../maps/png.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * Applies one grade to an RGBA image in place. Transparent pixels are left alone.
 * An optional `ceiling` (0–255) compresses anything brighter than it, so an
 * element such as water never outshines the scene.
 */
export function grade(image, settings) {
  const { saturation, brightness, gamma, shadowTint, shadowStrength, highlightTint, highlightStrength, ceiling = 255 } = settings;
  const { data } = image;
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] === 0) continue;
    let [r, g, b] = [data[i], data[i + 1], data[i + 2]];
    const luma = 0.299 * r + 0.587 * g + 0.114 * b;
    // 1. pull colours toward grey
    r = luma + (r - luma) * saturation;
    g = luma + (g - luma) * saturation;
    b = luma + (b - luma) * saturation;
    // 2. darken with a gamma curve
    [r, g, b] = [r, g, b].map((c) => 255 * Math.pow(Math.max(0, c * brightness) / 255, gamma));
    // 3. split toning: cold shadows, warm highlights
    const t = luma / 255;
    const shadow = (1 - t) * (1 - t) * shadowStrength;
    const highlight = t * t * highlightStrength;
    r += shadowTint[0] * shadow + highlightTint[0] * highlight;
    g += shadowTint[1] * shadow + highlightTint[1] * highlight;
    b += shadowTint[2] * shadow + highlightTint[2] * highlight;
    // 4. soft highlight ceiling
    const graded = 0.299 * r + 0.587 * g + 0.114 * b;
    if (graded > ceiling) {
      const k = (ceiling + (graded - ceiling) * 0.15) / graded;
      [r, g, b] = [r * k, g * k, b * k];
    }
    data[i] = Math.max(0, Math.min(255, Math.round(r)));
    data[i + 1] = Math.max(0, Math.min(255, Math.round(g)));
    data[i + 2] = Math.max(0, Math.min(255, Math.round(b)));
  }
  return image;
}

const runDirectly = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (runDirectly) {
  const [region, input, output] = process.argv.slice(2);
  if (!region || !input || !output) {
    console.error('usage: node tools/art/grade.mjs <region> <source.png> <out.png>');
    process.exit(1);
  }
  const grades = JSON.parse(readFileSync(path.join(ROOT, 'content', 'art', 'grades.json'), 'utf8'));
  const regionGrade = grades.regions[region];
  if (!regionGrade) throw new Error(`no grade for region ${region} in content/art/grades.json`);
  const source = readPng(readFileSync(input));
  const graded = grade(copyOf(source), regionGrade);
  // A file may override the grade for part of its pixels (e.g. darker, muddier
  // water while the grass on the same tiles keeps the region grade).
  const override = regionGrade.files?.[path.basename(input)];
  if (override) {
    const other = grade(copyOf(source), { ...regionGrade, ...override });
    const selected = pixelMatcher(override.match);
    for (let i = 0; i < source.data.length; i += 4) {
      if (selected(source.data[i], source.data[i + 1], source.data[i + 2])) graded.data.set(other.data.subarray(i, i + 4), i);
    }
  }
  mkdirSync(path.dirname(output), { recursive: true });
  writeFileSync(output, writePng(graded));
  console.log(`graded ${path.basename(input)} for ${region} → ${output}`);
}

/** Selects pixels by hue range (for saturated colours) or by lightness (for near-white). Without a rule, every pixel. */
function pixelMatcher(match) {
  if (!match) return () => true;
  const [minHue, maxHue] = match.hue;
  return (r, g, b) => {
    const max = Math.max(r, g, b) / 255;
    const min = Math.min(r, g, b) / 255;
    const lightness = (max + min) / 2;
    if (lightness >= match.minLightness) return true;
    const delta = max - min;
    if (delta < 0.08) return false;
    let hue;
    if (max === r / 255) hue = 60 * (((g - b) / 255 / delta) % 6);
    else if (max === g / 255) hue = 60 * ((b - r) / 255 / delta + 2);
    else hue = 60 * ((r - g) / 255 / delta + 4);
    hue = (hue + 360) % 360;
    return hue >= minHue && hue <= maxHue;
  };
}

function copyOf(image) {
  return { ...image, data: Buffer.from(image.data) };
}

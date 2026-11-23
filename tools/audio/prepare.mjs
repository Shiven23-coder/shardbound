#!/usr/bin/env node
// Prepares the game's audio from an extracted Ninja Adventure pack (P-42):
// sound effects become mono 16-bit WAV at 22.05 kHz (a quarter of the pack's
// stereo 44.1 kHz size, plenty for short effects); the music is copied as the
// pack's OGG, plus an AAC (.m4a) fallback for Safari on iPhones, which cannot
// play OGG everywhere (P-48). The OGG is decoded in Playwright's Chromium and
// encoded by Windows' Media Foundation (wav-to-m4a.ps1), so the music step
// runs on Windows. Run it again after changing the tables below.
//
//   node tools/audio/prepare.mjs <folder with the pack's files>
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const OUT = 'content/audio';
const RATE = 22050;
const MUSIC_FALLBACK_BITRATE = 64000;
const HERE = dirname(fileURLToPath(import.meta.url));

/** Pack file → file in content/audio. Every row needs a CREDITS.md row. */
const EFFECTS = {
  'Rain.wav': 'sfx/rain.wav',
  'Grass.wav': 'sfx/footstep-grass.wav',
  'Move1.wav': 'sfx/blip.wav',
  'Accept3.wav': 'sfx/click.wav',
  // battle (P-57)
  'Alert2.wav': 'sfx/battle-start.wav',
  'Hit1.wav': 'sfx/hit.wav',
  'Impact.wav': 'sfx/crit.wav',
  'Slash.wav': 'sfx/slash.wav',
  'Magic1.wav': 'sfx/magic.wav',
  'Fireball.wav': 'sfx/fire.wav',
  'Heal.wav': 'sfx/heal.wav',
  'Success1.wav': 'sfx/victory.wav',
  'GameOver2.wav': 'sfx/defeat.wav',
  // Lanterns and Lost Shards (P-61, P-62, P-64)
  'Fire.wav': 'sfx/lantern-kindle.wav',
  'Heal2.wav': 'sfx/lantern-rest.wav',
  'LevelUp1.wav': 'sfx/level-up.wav',
  'PowerUp1.wav': 'sfx/shards-recovered.wav',
};
const MUSIC = {
  '26 - Lost Village.ogg': 'music/eldmere.ogg',
  '17 - Fight.ogg': 'music/battle.ogg',
};

const source = process.argv[2];
if (!source) {
  console.error('Usage: node tools/audio/prepare.mjs <folder with the pack files>');
  process.exit(1);
}

for (const [from, to] of Object.entries(EFFECTS)) {
  const wav = readWav(readFileSync(join(source, from)));
  const out = writeWav(resample(toMono(wav), wav.rate, RATE), RATE);
  write(to, out);
  console.log(`${from} → ${to}: ${(out.length / 1024).toFixed(1)} KB, ${(out.length / 2 / RATE).toFixed(2)} s`);
}
for (const [from, to] of Object.entries(MUSIC)) {
  if (!process.argv.includes('--force') && existsSync(join(OUT, to)) && existsSync(join(OUT, to.replace(/\.ogg$/, '.m4a')))) {
    console.log(`${to}: already prepared (--force to redo)`);   // the AAC encoder is not byte-stable
    continue;
  }
  mkdirSync(dirname(join(OUT, to)), { recursive: true });
  copyFileSync(join(source, from), join(OUT, to));
  console.log(`${from} → ${to}: copied`);
  const fallback = to.replace(/\.ogg$/, '.m4a');
  const temp = mkdtempSync(join(tmpdir(), 'shardbound-audio-'));
  try {
    const wav = join(temp, 'music.wav');
    writeFileSync(wav, await decodeInBrowser(readFileSync(join(source, from))));
    execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(HERE, 'wav-to-m4a.ps1'),
      '-In', wav, '-Out', resolve(OUT, fallback), '-Bitrate', String(MUSIC_FALLBACK_BITRATE)], { stdio: 'inherit' });
  } finally {
    rmSync(temp, { recursive: true, force: true });
  }
  console.log(`${from} → ${fallback}: AAC fallback at ${MUSIC_FALLBACK_BITRATE / 1000} kbps`);
}

/** Decodes a compressed file with Chromium's Web Audio and returns it as 16-bit PCM WAV at its own rate. */
async function decodeInBrowser(bytes) {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    const { rate, channels, pcm } = await page.evaluate(async (base64) => {
      const data = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
      const audio = await new OfflineAudioContext(2, 1, 44100).decodeAudioData(data.buffer);
      const channels = Array.from({ length: audio.numberOfChannels }, (_, c) => audio.getChannelData(c));
      const out = new Int16Array(audio.length * channels.length);
      for (let i = 0; i < audio.length; i++) {
        channels.forEach((channel, c) => { out[i * channels.length + c] = Math.max(-32768, Math.min(32767, Math.round(channel[i] * 32767))); });
      }
      let binary = '';
      const view = new Uint8Array(out.buffer);
      for (let i = 0; i < view.length; i += 0x8000) binary += String.fromCharCode(...view.subarray(i, i + 0x8000));
      return { rate: audio.sampleRate, channels: channels.length, pcm: btoa(binary) };
    }, bytes.toString('base64'));
    return wavFrom(Buffer.from(pcm, 'base64'), rate, channels);
  } finally {
    await browser.close();
  }
}

function write(to, bytes) {
  mkdirSync(dirname(join(OUT, to)), { recursive: true });
  writeFileSync(join(OUT, to), bytes);
}

/** Reads a 16-bit PCM WAV: its rate and its samples per channel as floats in [-1, 1]. */
function readWav(buffer) {
  if (buffer.toString('ascii', 0, 4) !== 'RIFF' || buffer.toString('ascii', 8, 12) !== 'WAVE') {
    throw new Error('Not a WAV file');
  }
  let format;
  let data;
  for (let at = 12; at + 8 <= buffer.length;) {
    const id = buffer.toString('ascii', at, at + 4);
    const size = buffer.readUInt32LE(at + 4);
    if (id === 'fmt ') {
      format = { code: buffer.readUInt16LE(at + 8), channels: buffer.readUInt16LE(at + 10),
        rate: buffer.readUInt32LE(at + 12), bits: buffer.readUInt16LE(at + 22) };
    } else if (id === 'data') {
      data = buffer.subarray(at + 8, at + 8 + size);
    }
    at += 8 + size + (size % 2);
  }
  if (!format || !data) throw new Error('WAV without fmt or data chunk');
  if (format.code !== 1 || format.bits !== 16) throw new Error('Only 16-bit PCM WAV is supported');
  const frames = Math.floor(data.length / (2 * format.channels));
  const channels = Array.from({ length: format.channels }, () => new Float32Array(frames));
  for (let i = 0; i < frames; i++) {
    for (let c = 0; c < format.channels; c++) {
      channels[c][i] = data.readInt16LE((i * format.channels + c) * 2) / 32768;
    }
  }
  return { rate: format.rate, channels };
}

function toMono({ channels }) {
  const mono = new Float32Array(channels[0].length);
  for (const channel of channels) channel.forEach((v, i) => { mono[i] += v / channels.length; });
  return mono;
}

/** Box-filter resampling: each output sample averages the input samples it covers (enough for downsampling effects). */
function resample(samples, from, to) {
  if (from === to) return samples;
  const ratio = from / to;
  const out = new Float32Array(Math.floor(samples.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const start = Math.floor(i * ratio);
    const end = Math.max(start + 1, Math.floor((i + 1) * ratio));
    let sum = 0;
    for (let j = start; j < end; j++) sum += samples[j];
    out[i] = sum / (end - start);
  }
  return out;
}

/** Mono float samples as a 16-bit PCM WAV file. */
function writeWav(samples, rate) {
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((v, i) => data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(v * 32767))), i * 2));
  return wavFrom(data, rate, 1);
}

/** Interleaved 16-bit PCM bytes with a WAV header. */
function wavFrom(data, rate, channels) {
  const header = Buffer.alloc(44);
  header.write('RIFF', 0, 'ascii');
  header.writeUInt32LE(36 + data.length, 4);
  header.write('WAVE', 8, 'ascii');
  header.write('fmt ', 12, 'ascii');
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);                     // PCM
  header.writeUInt16LE(channels, 22);
  header.writeUInt32LE(rate, 24);
  header.writeUInt32LE(rate * 2 * channels, 28);   // bytes per second
  header.writeUInt16LE(2 * channels, 32);          // bytes per frame
  header.writeUInt16LE(16, 34);
  header.write('data', 36, 'ascii');
  header.writeUInt32LE(data.length, 40);
  return Buffer.concat([header, data]);
}

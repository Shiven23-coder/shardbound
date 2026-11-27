// Sound (P-42): Web Audio with three gains (master for mute, music, effects)
// that follow the player's settings. Nothing plays until the first user
// interaction unlocks the AudioContext, as browsers require; sounds asked for
// before that (the rain, the region's music) start then. Effects are decoded
// once and kept; music streams through a media element so a long track is
// never held decoded in memory. File names and levels come from
// content/audio/audio.json, whose packs say when each file downloads (P-59):
// the interface sounds with the first page, a region's music and ambience on
// entering it, the battle track and effects only when a creep is near. A
// boss's track steps up in its second phase: faster, its pitch kept, louder (P-67).
import manifest from '../../../content/audio/audio.json';
import { getSettings, subscribeSettings } from '../settings/settings';
import type { Settings } from '../settings/settings';

export type Effect = keyof typeof manifest.effects;

interface Sound {
  file: string;
  volume: number;
  loop?: boolean;
}

const fileUrls = import.meta.glob('../../../content/audio/**/*.{wav,ogg,m4a}', {
  query: '?url',
  import: 'default',
  eager: true,
}) as Record<string, string>;

interface Track {
  file: string;
  /** AAC, for browsers that cannot play OGG Vorbis (Safari on older iPhones, P-48). */
  fallback: string;
  volume: number;
  /** How the track plays once stepped up (a boss's second phase, P-67). */
  escalated?: { rate: number; volume: number };
}

/** How fast and how loud a track plays, stepped up or not (a track with no step-up plays as it is). */
export function musicLevel(track: Track, escalated: boolean): { rate: number; volume: number } {
  return escalated && track.escalated ? track.escalated : { rate: 1, volume: track.volume };
}

const effects: Record<Effect, Sound> = manifest.effects;
const music: Record<string, Track> = manifest.music;

/** A region's or the battle's sounds: a music track and the effects that go with it. */
interface Pack {
  music: string;
  effects: Effect[];
}

/** When each sound downloads (P-59): each boss's pack on entering its arena (P-67). */
export const audioPacks = manifest.packs as unknown as {
  initial: { effects: Effect[] };
  regions: Record<string, Pack>;
  battle: Pack;
  bosses: Record<string, Pack>;
};

export const audioTiming = {
  footstepIntervalMs: manifest.footstepIntervalMs,
  footstepPitchJitter: manifest.footstepPitchJitter,
  blipEveryChars: manifest.blipEveryChars,
};

function urlOf(file: string): string {
  const url = fileUrls[`../../../content/audio/${file}`];
  if (!url) throw new Error(`No audio file ${file} in the build`);
  return url;
}

/** The file of a track this browser can play: the OGG where it can, else the AAC. */
export function playableFile(track: Track, canPlay: (mimeType: string) => boolean): string {
  return canPlay('audio/ogg; codecs="vorbis"') ? track.file : track.fallback;
}

/** The gain each bus should have for these settings. */
export function busLevels(settings: Settings): { master: number; music: number; effects: number } {
  return {
    master: settings.muted ? 0 : 1,
    music: settings.musicVolume / 100,
    effects: settings.effectsVolume / 100,
  };
}

interface Playing {
  region: string;
  element: HTMLAudioElement;
  gain: GainNode;
}

class AudioEngine {
  private context: AudioContext | null = null;
  private master!: GainNode;
  private musicBus!: GainNode;
  private effectsBus!: GainNode;
  /** Effect files as downloaded; they are decoded once sound is unlocked. */
  private readonly files = new Map<Effect, Promise<ArrayBuffer | null>>();
  private readonly buffers = new Map<Effect, Promise<AudioBuffer | null>>();
  private readonly loops = new Map<Effect, AudioBufferSourceNode>();
  /** What should be playing once sound is unlocked, and while it is. */
  private readonly wantedLoops = new Set<Effect>();
  private wantedMusic: string | null = null;
  /** The music is stepped up (a boss's second phase). */
  private escalated = false;
  private track: Playing | null = null;
  /** Tracks asked for ahead of time (the battle music when a creep is near, P-57), loading but not playing. */
  private readonly prefetched = new Map<string, HTMLAudioElement>();

  /** True once the browser lets sound play. */
  get unlocked(): boolean {
    return this.context?.state === 'running';
  }

  /** Called from user gestures until it succeeds: creates and resumes the context, then starts what is wanted. */
  async unlock(): Promise<void> {
    if (!this.context) {
      const Context = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Context) return;
      this.context = new Context();
      this.master = this.context.createGain();
      this.musicBus = this.context.createGain();
      this.effectsBus = this.context.createGain();
      this.musicBus.connect(this.master);
      this.effectsBus.connect(this.master);
      this.master.connect(this.context.destination);
      this.applySettings(getSettings(), 0);
      subscribeSettings(() => this.applySettings(getSettings(), 0.05));
    }
    if (this.context.state !== 'running') await this.context.resume().catch(() => undefined);
    if (this.unlocked) {
      this.wantedLoops.forEach((name) => void this.startLoop(name));
      this.updateMusic();
    }
  }

  /** Plays an effect once; `rate` changes its pitch and speed a little (footsteps). */
  play(name: Effect, rate = 1): void {
    if (!this.unlocked) return;
    void this.buffer(name).then((buffer) => {
      if (!buffer || !this.context) return;
      const source = this.context.createBufferSource();
      source.buffer = buffer;
      source.playbackRate.value = rate;
      source.connect(this.gainFor(effects[name].volume, this.effectsBus));
      source.start();
    });
  }

  /** Starts or stops a looping effect (the rain). */
  setLoop(name: Effect, on: boolean): void {
    if (on) {
      this.wantedLoops.add(name);
      if (this.unlocked) void this.startLoop(name);
    } else {
      this.wantedLoops.delete(name);
      this.loops.get(name)?.stop();
      this.loops.delete(name);
    }
  }

  /** Downloads the interface sounds with the first page (P-59). */
  loadInitial(): void {
    audioPacks.initial.effects.forEach((name) => void this.file(name));
  }

  /** Downloads a region's music and ambience on entering it (P-59). */
  loadRegion(region: string): void {
    const pack = audioPacks.regions[region];
    if (pack) this.loadPack(pack);
  }

  /** Downloads the battle track and effects when a creep is near; never with the first page (P-57). */
  loadBattle(): void {
    this.loadPack(audioPacks.battle);
  }

  /** Downloads a boss's track and stings on entering its arena (P-64, P-67). */
  loadBoss(bossId: string): void {
    const pack = audioPacks.bosses[bossId];
    if (pack) this.loadPack(pack);
  }

  private loadPack(pack: Pack): void {
    pack.effects.forEach((name) => void this.file(name));
    this.prefetchMusic(pack.music);
  }

  /** Starts loading a track so it can play at once later. */
  private prefetchMusic(name: string): void {
    if (this.prefetched.has(name) || this.track?.region === name || !music[name]) return;
    const element = new Audio(this.trackUrl(music[name]!));
    element.preload = 'auto';
    element.loop = true;
    this.prefetched.set(name, element);
  }

  private trackUrl(track: Track): string {
    const probe = document.createElement('audio');
    return urlOf(playableFile(track, (type) => probe.canPlayType(type) !== ''));
  }

  /** Steps the music up or back (a boss's second phase, P-67), on the track playing and any that follows. */
  setEscalated(on: boolean): void {
    if (on === this.escalated) return;
    this.escalated = on;
    const playing = this.track;
    if (!playing || !this.context) return;
    this.applyLevel(playing, manifest.musicFadeMs / 1000 / 3);
  }

  /** The music of a region, or silence (null). Changing region fades one track out and the next in. */
  setMusic(region: string | null): void {
    this.wantedMusic = region && music[region] ? region : null;
    if (this.unlocked) this.updateMusic();
  }

  private applySettings(settings: Settings, smoothing: number): void {
    if (!this.context) return;
    const levels = busLevels(settings);
    const now = this.context.currentTime;
    this.master.gain.setTargetAtTime(levels.master, now, smoothing);
    this.musicBus.gain.setTargetAtTime(levels.music, now, smoothing);
    this.effectsBus.gain.setTargetAtTime(levels.effects, now, smoothing);
  }

  private gainFor(volume: number, bus: GainNode): GainNode {
    const gain = this.context!.createGain();
    gain.gain.value = volume;
    gain.connect(bus);
    return gain;
  }

  private file(name: Effect): Promise<ArrayBuffer | null> {
    let loading = this.files.get(name);
    if (!loading) {
      loading = fetch(urlOf(effects[name].file))
        .then((response) => response.arrayBuffer())
        .catch(() => null); // a sound that cannot load stays silent; the game goes on
      this.files.set(name, loading);
    }
    return loading;
  }

  private buffer(name: Effect): Promise<AudioBuffer | null> {
    let decoding = this.buffers.get(name);
    if (!decoding) {
      decoding = this.file(name)
        .then((bytes) => (bytes && this.context ? this.context.decodeAudioData(bytes) : null))
        .catch(() => null);
      this.buffers.set(name, decoding);
    }
    return decoding;
  }

  private async startLoop(name: Effect): Promise<void> {
    if (this.loops.has(name)) return;
    const buffer = await this.buffer(name);
    if (!buffer || !this.context || !this.wantedLoops.has(name) || this.loops.has(name)) return;
    const source = this.context.createBufferSource();
    source.buffer = buffer;
    source.loop = true;
    source.connect(this.gainFor(effects[name].volume, this.effectsBus));
    source.start();
    this.loops.set(name, source);
  }

  private updateMusic(): void {
    if (this.track?.region === this.wantedMusic) return;
    const fade = manifest.musicFadeMs / 1000;
    const old = this.track;
    if (old) {
      const now = this.context!.currentTime;
      old.gain.gain.setTargetAtTime(0, now, fade / 3);
      setTimeout(() => {
        old.element.pause();
        old.gain.disconnect();
      }, manifest.musicFadeMs);
    }
    this.track = null;
    if (!this.wantedMusic || !this.context) return;
    const sound = music[this.wantedMusic]!;
    const element = this.prefetched.get(this.wantedMusic) ?? new Audio(this.trackUrl(sound));
    this.prefetched.delete(this.wantedMusic);
    element.loop = true;
    element.preload = 'auto';
    element.currentTime = 0;
    const gain = this.gainFor(0, this.musicBus);
    this.context.createMediaElementSource(element).connect(gain);
    this.track = { region: this.wantedMusic, element, gain };
    this.applyLevel(this.track, fade / 3);
    void element.play().catch(() => undefined);
  }

  private applyLevel(playing: Playing, smoothing: number): void {
    const level = musicLevel(music[playing.region]!, this.escalated);
    playing.element.preservesPitch = true;
    playing.element.playbackRate = level.rate;
    playing.gain.gain.setTargetAtTime(level.volume, this.context!.currentTime, smoothing);
  }
}

export const audio = new AudioEngine();

/**
 * Unlocks sound on the first user gestures and clicks on interface buttons.
 * The touch controls are the game's own input and stay silent.
 */
export function installAudio(): void {
  audio.loadInitial();
  const gestures = ['pointerdown', 'pointerup', 'keydown', 'touchend', 'click'] as const;
  const onGesture = () => {
    void audio.unlock().then(() => {
      if (audio.unlocked) gestures.forEach((type) => window.removeEventListener(type, onGesture, true));
    });
  };
  gestures.forEach((type) => window.addEventListener(type, onGesture, true));
  document.addEventListener('click', (event) => {
    const button = (event.target as Element | null)?.closest?.('button');
    if (button && !button.disabled && !button.closest('.dpad, .interact-button')) audio.play('click');
  });
}

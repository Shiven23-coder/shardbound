// Full screen on phones (P-53): the game takes the whole screen, browser bars
// included, the way a video does. The browser allows it only from a tap.
import { useEffect, useState } from 'react';

/** The browser can put the page in full screen (Android Chrome can; iPhone Safari cannot). */
export function fullscreenSupported(): boolean {
  return typeof document !== 'undefined' && document.fullscreenEnabled === true
    && typeof document.documentElement.requestFullscreen === 'function';
}

export function isFullscreen(): boolean {
  return document.fullscreenElement !== null;
}

/** Asks for full screen; call it only from a tap or click. A refusal changes nothing. */
export function enterFullscreen(): void {
  document.documentElement.requestFullscreen({ navigationUI: 'hide' }).catch(() => undefined);
}

/** Whether the page is in full screen now, kept current. */
export function useFullscreen(): boolean {
  const [fullscreen, setFullscreen] = useState(() => fullscreenSupported() && isFullscreen());
  useEffect(() => {
    const changed = () => setFullscreen(isFullscreen());
    document.addEventListener('fullscreenchange', changed);
    return () => document.removeEventListener('fullscreenchange', changed);
  }, []);
  return fullscreen;
}

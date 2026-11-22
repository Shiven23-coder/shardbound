// Holding a touch control must not start the browser's own long-press gesture:
// Android Chrome buzzes, plays a sound and starts a selection, and may cancel
// the touch. Cancelling touchstart prevents that; pointer events, which the
// controls use, still arrive. It needs a non-passive native listener: React
// registers touch listeners as passive, where preventDefault does nothing.
import { useEffect, type RefObject } from 'react';

export function useNoBrowserGestures(ref: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    const cancel = (event: TouchEvent) => {
      if (event.cancelable) event.preventDefault();
    };
    element.addEventListener('touchstart', cancel, { passive: false });
    return () => element.removeEventListener('touchstart', cancel);
  }, [ref]);
}

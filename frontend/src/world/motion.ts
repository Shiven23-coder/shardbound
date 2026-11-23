/** The player asked for less motion (the OS or browser setting): no shakes, flashes, lunges or pulses. */
export function reducedMotion(): boolean {
  return typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

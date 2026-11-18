// Display-only formatting helpers (no game rules).

// 412 -> "6:52". Returns "—" when there is no value.
export function formatSeconds(seconds) {
  if (seconds === null || seconds === undefined) return '—';
  const minutes = Math.floor(seconds / 60);
  const rest = String(seconds % 60).padStart(2, '0');
  return `${minutes}:${rest}`;
}

// Turns a server timestamp into local text, or shows it as-is if unparsable.
export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

// Width for a progress bar, clamped to 0..100 %.
export function percent(value, max) {
  if (!max || max <= 0) return 0;
  return Math.max(0, Math.min(100, (value / max) * 100));
}

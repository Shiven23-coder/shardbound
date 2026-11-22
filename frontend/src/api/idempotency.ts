// Idempotency keys (P-31, P-56): every state-changing request carries a new
// UUID, and a retry after an outage sends the same one, so the server applies
// an action at most once. Built with getRandomValues because randomUUID needs
// a secure context, and phones on the local network use plain http.

/** A random version-4 UUID. */
export function newIdempotencyKey(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export const IDEMPOTENCY_HEADER = 'Idempotency-Key';

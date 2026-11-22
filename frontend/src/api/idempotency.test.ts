import { describe, expect, it } from 'vitest';
import { newIdempotencyKey } from './idempotency';

describe('idempotency keys (P-56)', () => {
  it('are version-4 UUIDs the server accepts', () => {
    expect(newIdempotencyKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('are new every time', () => {
    const keys = new Set(Array.from({ length: 100 }, newIdempotencyKey));
    expect(keys.size).toBe(100);
  });
});

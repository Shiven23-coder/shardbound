import { describe, expect, it } from 'vitest';
import { formatDate, formatSeconds, percent } from './format';

describe('formatSeconds', () => {
  it('formats m:ss with a zero-padded seconds part', () => {
    expect(formatSeconds(0)).toBe('0:00');
    expect(formatSeconds(59)).toBe('0:59');
    expect(formatSeconds(60)).toBe('1:00');
    expect(formatSeconds(412)).toBe('6:52');
    expect(formatSeconds(3599)).toBe('59:59');
  });

  it('shows a dash when there is no value', () => {
    expect(formatSeconds(null)).toBe('—');
    expect(formatSeconds(undefined)).toBe('—');
  });
});

describe('formatDate', () => {
  it('shows a dash for empty values and unparsable text as-is', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('')).toBe('—');
    expect(formatDate('yesterday-ish')).toBe('yesterday-ish');
  });

  it('formats a parsable timestamp in local time', () => {
    const stamp = '2026-11-18T10:30:00Z';
    expect(formatDate(stamp)).toBe(new Date(stamp).toLocaleString());
  });
});

describe('percent', () => {
  it('returns value/max as a percentage clamped to 0..100', () => {
    expect(percent(15, 30)).toBe(50);
    expect(percent(45, 30)).toBe(100);
    expect(percent(-5, 30)).toBe(0);
  });

  it('returns 0 when max is zero or negative', () => {
    expect(percent(5, 0)).toBe(0);
    expect(percent(5, -10)).toBe(0);
  });
});

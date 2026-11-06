import { RelativeTimePipe } from './relative-time.pipe';

describe('RelativeTimePipe', () => {
  const pipe = new RelativeTimePipe();
  const now = new Date('2026-11-06T10:10:00Z');

  it('formats relative times', () => {
    expect(pipe.transform('2026-11-06T10:09:58Z', now)).toBe('just now');
    expect(pipe.transform('2026-11-06T10:09:30Z', now)).toBe('30s ago');
    expect(pipe.transform('2026-11-06T10:05:00Z', now)).toBe('5m ago');
    expect(pipe.transform('2026-11-06T08:10:00Z', now)).toBe('2h ago');
    expect(pipe.transform(null)).toBe('');
  });
});

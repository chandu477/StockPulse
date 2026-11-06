import { marginPct } from './margin';

describe('marginPct', () => {
  it('computes gross margin and handles unknown cost', () => {
    expect(marginPct(25, 11.2)).toBeCloseTo(55.2, 1);
    expect(marginPct(25, null)).toBeNull();
    expect(marginPct(0, 5)).toBeNull();
  });
});

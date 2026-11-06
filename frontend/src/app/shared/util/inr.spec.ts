import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { formatInr } from './inr';

describe('formatInr', () => {
  beforeAll(() => registerLocaleData(localeEnIn));

  it('formats rupees with Indian grouping', () => {
    expect(formatInr(1999)).toBe('₹1,999');
    expect(formatInr(878.9)).toBe('₹878.90');
    expect(formatInr(108999.5)).toBe('₹1,08,999.50');
    expect(formatInr(null)).toBe('—');
  });
});

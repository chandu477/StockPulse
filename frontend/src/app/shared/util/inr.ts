import { formatCurrency } from '@angular/common';

/** Indian rupee formatting (en-IN grouping, e.g. ₹1,999 or ₹1,08,999.50). Paise only when present. */
export function formatInr(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  const digits = Number.isInteger(value) ? '1.0-0' : '1.2-2';
  return formatCurrency(value, 'en-IN', '₹', 'INR', digits);
}

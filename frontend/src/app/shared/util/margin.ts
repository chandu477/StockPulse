/** Gross margin % = (price - cost) / price * 100, or null when cost is unknown. */
export function marginPct(price: number, cost: number | null | undefined): number | null {
  if (cost === null || cost === undefined || price <= 0) {
    return null;
  }
  return ((price - cost) / price) * 100;
}

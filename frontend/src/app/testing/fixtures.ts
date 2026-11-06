import { PricingSuggestion, Product, ReorderSuggestion } from '../core/api/api.models';

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'PRD-003',
    sku: 'SKU-APP-001',
    name: 'Organic Cotton T-Shirt',
    category: 'APPAREL',
    currentPrice: 24.99,
    stockLevel: 7,
    reorderThreshold: 15,
    demandVelocity: 13,
    status: 'PRICE_REVIEW_PENDING',
    costPrice: null,
    marginFloor: null,
    supplierId: null,
    marginPct: null,
    pendingPricingCount: 1,
    pendingReorderCount: 1,
    createdAt: '2026-11-06T10:00:00Z',
    updatedAt: '2026-11-06T10:00:00Z',
    ...overrides,
  };
}

export function pricing(overrides: Partial<PricingSuggestion> = {}): PricingSuggestion {
  return {
    id: 1,
    productId: 'PRD-003',
    productName: 'Organic Cotton T-Shirt',
    sku: 'SKU-APP-001',
    currentPrice: 24.99,
    recommendedPrice: 27.49,
    changePct: 10,
    direction: 'INCREASE',
    confidence: 0.7,
    reasoning: 'Stock 7 is below the reorder threshold 15.',
    status: 'PENDING',
    triggerReason: 'INVENTORY_LOW',
    source: 'RULE',
    strategyKey: 'rule',
    fallbackReason: null,
    createdAt: '2026-11-06T10:00:01Z',
    decidedAt: null,
    decisionNote: null,
    ...overrides,
  };
}

export function reorder(overrides: Partial<ReorderSuggestion> = {}): ReorderSuggestion {
  return {
    id: 1,
    productId: 'PRD-003',
    productName: 'Organic Cotton T-Shirt',
    sku: 'SKU-APP-001',
    currentStock: 7,
    recommendedQuantity: 38,
    leadTimeDays: 7,
    confidence: 0.6,
    reasoning: 'Restock to 3x the threshold.',
    status: 'PENDING',
    triggerReason: 'INVENTORY_LOW',
    source: 'RULE',
    strategyKey: 'rule',
    fallbackReason: null,
    createdAt: '2026-11-06T10:00:01Z',
    decidedAt: null,
    decisionNote: null,
    ...overrides,
  };
}

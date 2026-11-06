// Types mirror contracts/openapi.yaml exactly. Do not add fields that are not in the contract.

export type Category = 'ELECTRONICS' | 'APPAREL' | 'HOME';
export type ProductStatus = 'ACTIVE' | 'PRICE_REVIEW_PENDING' | 'OUT_OF_STOCK';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'SUPERSEDED';
export type TriggerReason = 'INITIAL' | 'INVENTORY_LOW' | 'DEMAND_SPIKE' | 'MANUAL';
export type Direction = 'INCREASE' | 'DECREASE' | 'HOLD';
export type SuggestionSource = 'AI' | 'RULE' | 'RULE_FALLBACK';
export type FallbackReason =
  | 'TIMEOUT'
  | 'RATE_LIMITED'
  | 'PROVIDER_ERROR'
  | 'EMPTY_RESPONSE'
  | 'INVALID_JSON'
  | 'OUT_OF_BOUNDS'
  | 'CIRCUIT_OPEN'
  | 'BUDGET_EXCEEDED'
  | 'NOT_CONFIGURED';

export const CATEGORIES: readonly Category[] = ['ELECTRONICS', 'APPAREL', 'HOME'];
export const PRODUCT_STATUSES: readonly ProductStatus[] = [
  'ACTIVE',
  'PRICE_REVIEW_PENDING',
  'OUT_OF_STOCK',
];

export interface Product {
  id: string;
  sku: string;
  name: string;
  category: Category;
  currentPrice: number;
  stockLevel: number;
  reorderThreshold: number;
  demandVelocity: number;
  status: ProductStatus;
  costPrice: number | null;
  marginFloor: number | null;
  supplierId: string | null;
  marginPct: number | null;
  pendingPricingCount: number;
  pendingReorderCount: number;
  createdAt: string;
  updatedAt: string;
}

interface SuggestionBase {
  id: number;
  productId: string;
  productName: string;
  sku: string;
  confidence: number;
  /** Untrusted model output: render as text only. */
  reasoning: string;
  status: SuggestionStatus;
  triggerReason: TriggerReason;
  source: SuggestionSource;
  strategyKey: string;
  fallbackReason: FallbackReason | null;
  createdAt: string;
  decidedAt: string | null;
  decisionNote: string | null;
}

export interface PricingSuggestion extends SuggestionBase {
  currentPrice: number;
  recommendedPrice: number;
  changePct: number;
  direction: Direction;
}

export interface ReorderSuggestion extends SuggestionBase {
  currentStock: number;
  recommendedQuantity: number;
  leadTimeDays: number;
}

export interface PricePoint {
  at: string;
  price: number;
  suggestionId: number;
}

export type Decision = 'ACCEPTED' | 'REJECTED';

export interface DecisionRequest {
  status: Decision;
  note?: string | null;
}

export interface StrategySettings {
  pricing: string;
  reorder: string;
  available: { pricing: string[]; reorder: string[] };
}

export interface StrategyUpdateRequest {
  pricing?: string;
  reorder?: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
  errors: FieldError[];
}

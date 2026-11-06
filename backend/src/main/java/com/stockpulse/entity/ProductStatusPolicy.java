package com.stockpulse.entity;

/** ADR-7: stock 0 -> OUT_OF_STOCK; any PENDING suggestion -> PRICE_REVIEW_PENDING; else ACTIVE. */
public final class ProductStatusPolicy {
  private ProductStatusPolicy() {}

  public static ProductStatus derive(int stockLevel, boolean hasPendingSuggestion) {
    if (stockLevel == 0) {
      return ProductStatus.OUT_OF_STOCK;
    }
    if (hasPendingSuggestion) {
      return ProductStatus.PRICE_REVIEW_PENDING;
    }
    return ProductStatus.ACTIVE;
  }
}

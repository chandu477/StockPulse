package com.stockpulse.entity;

import java.math.BigDecimal;

/** Read-only snapshot handed to other modules (plan §C.3). Safe to use outside a transaction. */
public record ProductView(
    String id,
    String sku,
    String name,
    Category category,
    BigDecimal currentPrice,
    int stockLevel,
    int reorderThreshold,
    int demandVelocity,
    BigDecimal costPrice,
    BigDecimal marginFloor,
    String supplierId) {

  public static ProductView of(Product p) {
    return new ProductView(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getCategory(),
        p.getCurrentPrice(),
        p.getStockLevel(),
        p.getReorderThreshold(),
        p.getDemandVelocity(),
        p.getCostPrice(),
        p.getMarginFloor(),
        p.getSupplierId());
  }
}

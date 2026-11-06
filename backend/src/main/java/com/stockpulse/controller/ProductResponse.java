package com.stockpulse.controller;

import com.stockpulse.entity.Category;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.service.ProductService.ProductDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record ProductResponse(
    String id,
    String sku,
    String name,
    Category category,
    BigDecimal currentPrice,
    int stockLevel,
    int reorderThreshold,
    int demandVelocity,
    ProductStatus status,
    BigDecimal costPrice,
    BigDecimal marginFloor,
    String supplierId,
    BigDecimal marginPct,
    long pendingPricingCount,
    long pendingReorderCount,
    Instant createdAt,
    Instant updatedAt) {

  public static ProductResponse from(ProductDetails d) {
    Product p = d.product();
    return new ProductResponse(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getCategory(),
        p.getCurrentPrice(),
        p.getStockLevel(),
        p.getReorderThreshold(),
        p.getDemandVelocity(),
        p.getStatus(),
        p.getCostPrice(),
        p.getMarginFloor(),
        p.getSupplierId(),
        marginPct(p),
        d.pending().pricing(),
        d.pending().reorder(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  private static BigDecimal marginPct(Product p) {
    if (p.getCostPrice() == null) {
      return null;
    }
    return p.getCurrentPrice()
        .subtract(p.getCostPrice())
        .multiply(BigDecimal.valueOf(100))
        .divide(p.getCurrentPrice(), 2, RoundingMode.HALF_UP);
  }
}

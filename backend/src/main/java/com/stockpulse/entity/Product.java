package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
  @Id private String id;

  @Column(nullable = false, unique = true)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Category category;

  @Column(name = "current_price", nullable = false)
  private BigDecimal currentPrice;

  @Column(name = "stock_level", nullable = false)
  private int stockLevel;

  @Column(name = "reorder_threshold", nullable = false)
  private int reorderThreshold;

  @Column(name = "demand_velocity", nullable = false)
  private int demandVelocity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProductStatus status;

  /** Sprint 2 seam: margin-aware pricing (ADR-6). */
  @Column(name = "cost_price")
  private BigDecimal costPrice;

  /** Sprint 2 seam: MarginFloorGuardrail (ADR-6). */
  @Column(name = "margin_floor")
  private BigDecimal marginFloor;

  /** Sprint 2 seam: supplier catalog (ADR-6). */
  @Column(name = "supplier_id")
  private String supplierId;

  @Version private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Product() {}

  public Product(
      String id,
      String sku,
      String name,
      Category category,
      BigDecimal currentPrice,
      int stockLevel,
      int reorderThreshold,
      int demandVelocity,
      BigDecimal costPrice,
      String supplierId,
      Instant now) {
    this.id = id;
    this.sku = sku;
    this.name = name;
    this.category = category;
    this.currentPrice = money(currentPrice);
    this.stockLevel = stockLevel;
    this.reorderThreshold = reorderThreshold;
    this.demandVelocity = demandVelocity;
    this.costPrice = costPrice == null ? null : money(costPrice);
    this.supplierId = supplierId;
    this.status = ProductStatusPolicy.derive(stockLevel, false);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Simulated sale: stock down, velocity up (Q-2). Caller has checked stock is sufficient. */
  public void recordSale(int quantity, Instant now) {
    if (quantity > stockLevel) {
      throw new IllegalStateException("Insufficient stock");
    }
    this.stockLevel -= quantity;
    this.demandVelocity += quantity;
    this.updatedAt = now;
  }

  public void adjustStock(int newLevel, Instant now) {
    this.stockLevel = newLevel;
    this.updatedAt = now;
  }

  public void receiveInbound(int quantity, Instant now) {
    this.stockLevel += quantity;
    this.updatedAt = now;
  }

  /** Only called when a PricingSuggestion is accepted (BR-1). */
  public void applyAcceptedPrice(BigDecimal newPrice, Instant now) {
    this.currentPrice = money(newPrice);
    this.updatedAt = now;
  }

  public void applyDerivedStatus(ProductStatus derived, Instant now) {
    if (this.status != derived) {
      this.status = derived;
      this.updatedAt = now;
    }
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public String getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public Category getCategory() {
    return category;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public int getStockLevel() {
    return stockLevel;
  }

  public int getReorderThreshold() {
    return reorderThreshold;
  }

  public int getDemandVelocity() {
    return demandVelocity;
  }

  public ProductStatus getStatus() {
    return status;
  }

  public BigDecimal getCostPrice() {
    return costPrice;
  }

  public BigDecimal getMarginFloor() {
    return marginFloor;
  }

  public String getSupplierId() {
    return supplierId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only stock history. Sprint 2 recomputes rolling velocity from these rows (ADR-6). */
@Entity
@Table(name = "inventory_events")
public class InventoryEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private String productId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private InventoryEventType type;

  @Column(name = "quantity_delta", nullable = false)
  private int quantityDelta;

  @Column(name = "stock_after", nullable = false)
  private int stockAfter;

  @Column(name = "velocity_after", nullable = false)
  private int velocityAfter;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InventoryEvent() {}

  public InventoryEvent(
      String productId,
      InventoryEventType type,
      int quantityDelta,
      int stockAfter,
      int velocityAfter,
      Instant createdAt) {
    this.productId = productId;
    this.type = type;
    this.quantityDelta = quantityDelta;
    this.stockAfter = stockAfter;
    this.velocityAfter = velocityAfter;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public InventoryEventType getType() {
    return type;
  }

  public int getQuantityDelta() {
    return quantityDelta;
  }
}

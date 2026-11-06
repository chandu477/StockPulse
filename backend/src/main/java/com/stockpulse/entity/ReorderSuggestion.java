package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reorder_suggestions")
public class ReorderSuggestion extends Suggestion {
  @Column(name = "current_stock", nullable = false)
  private int currentStock;

  @Column(name = "recommended_quantity", nullable = false)
  private int recommendedQuantity;

  @Column(name = "lead_time_days", nullable = false)
  private int leadTimeDays;

  protected ReorderSuggestion() {}

  public ReorderSuggestion(
      String productId,
      int currentStock,
      int recommendedQuantity,
      int leadTimeDays,
      double confidence,
      String reasoning,
      TriggerReason triggerReason,
      SuggestionSource source,
      String strategyKey,
      FallbackReason fallbackReason,
      Instant createdAt) {
    super(
        productId,
        confidence,
        reasoning,
        triggerReason,
        source,
        strategyKey,
        fallbackReason,
        createdAt);
    this.currentStock = currentStock;
    this.recommendedQuantity = recommendedQuantity;
    this.leadTimeDays = leadTimeDays;
  }

  public int getCurrentStock() {
    return currentStock;
  }

  public int getRecommendedQuantity() {
    return recommendedQuantity;
  }

  public int getLeadTimeDays() {
    return leadTimeDays;
  }
}

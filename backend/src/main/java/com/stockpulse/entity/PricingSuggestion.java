package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "pricing_suggestions")
public class PricingSuggestion extends Suggestion {
  @Column(name = "current_price", nullable = false)
  private BigDecimal currentPrice;

  @Column(name = "recommended_price", nullable = false)
  private BigDecimal recommendedPrice;

  @Column(name = "change_pct", nullable = false)
  private BigDecimal changePct;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Direction direction;

  protected PricingSuggestion() {}

  public PricingSuggestion(
      String productId,
      BigDecimal currentPrice,
      BigDecimal recommendedPrice,
      Direction direction,
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
    this.currentPrice = currentPrice.setScale(2, RoundingMode.HALF_UP);
    this.recommendedPrice = recommendedPrice.setScale(2, RoundingMode.HALF_UP);
    this.direction = direction;
    this.changePct =
        this.recommendedPrice
            .subtract(this.currentPrice)
            .multiply(BigDecimal.valueOf(100))
            .divide(this.currentPrice, 2, RoundingMode.HALF_UP);
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public BigDecimal getRecommendedPrice() {
    return recommendedPrice;
  }

  public BigDecimal getChangePct() {
    return changePct;
  }

  public Direction getDirection() {
    return direction;
  }
}

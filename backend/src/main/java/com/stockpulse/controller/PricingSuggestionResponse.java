package com.stockpulse.controller;

import com.stockpulse.entity.Direction;
import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.PricingSuggestion;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.TriggerReason;
import java.math.BigDecimal;
import java.time.Instant;

public record PricingSuggestionResponse(
    long id,
    String productId,
    String productName,
    String sku,
    BigDecimal currentPrice,
    BigDecimal recommendedPrice,
    BigDecimal changePct,
    Direction direction,
    BigDecimal confidence,
    String reasoning,
    SuggestionStatus status,
    TriggerReason triggerReason,
    SuggestionSource source,
    String strategyKey,
    FallbackReason fallbackReason,
    Instant createdAt,
    Instant decidedAt,
    String decisionNote) {

  public static PricingSuggestionResponse from(PricingSuggestion s, ProductView p) {
    return new PricingSuggestionResponse(
        s.getId(),
        s.getProductId(),
        p == null ? null : p.name(),
        p == null ? null : p.sku(),
        s.getCurrentPrice(),
        s.getRecommendedPrice(),
        s.getChangePct(),
        s.getDirection(),
        s.getConfidence(),
        s.getReasoning(),
        s.getStatus(),
        s.getTriggerReason(),
        s.getSource(),
        s.getStrategyKey(),
        s.getFallbackReason(),
        s.getCreatedAt(),
        s.getDecidedAt(),
        s.getDecisionNote());
  }
}

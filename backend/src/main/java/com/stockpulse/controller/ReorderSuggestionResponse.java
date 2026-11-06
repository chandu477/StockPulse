package com.stockpulse.controller;

import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.ReorderSuggestion;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.TriggerReason;
import java.math.BigDecimal;
import java.time.Instant;

public record ReorderSuggestionResponse(
    long id,
    String productId,
    String productName,
    String sku,
    int currentStock,
    int recommendedQuantity,
    int leadTimeDays,
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

  public static ReorderSuggestionResponse from(ReorderSuggestion s, ProductView p) {
    return new ReorderSuggestionResponse(
        s.getId(),
        s.getProductId(),
        p == null ? null : p.name(),
        p == null ? null : p.sku(),
        s.getCurrentStock(),
        s.getRecommendedQuantity(),
        s.getLeadTimeDays(),
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

package com.stockpulse.recommendation.strategy;

import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.SuggestionSource;

public record ReorderAdvice(
    int recommendedQuantity,
    int leadTimeDays,
    double confidence,
    String reasoning,
    SuggestionSource source,
    FallbackReason fallbackReason) {

  public ReorderAdvice asFallback(FallbackReason reason) {
    return new ReorderAdvice(
        recommendedQuantity,
        leadTimeDays,
        confidence,
        reasoning,
        SuggestionSource.RULE_FALLBACK,
        reason);
  }
}

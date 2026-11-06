package com.stockpulse.recommendation.strategy;

import com.stockpulse.entity.Direction;
import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.SuggestionSource;
import java.math.BigDecimal;

public record PricingAdvice(
    BigDecimal recommendedPrice,
    Direction direction,
    double confidence,
    String reasoning,
    SuggestionSource source,
    FallbackReason fallbackReason) {

  public PricingAdvice asFallback(FallbackReason reason) {
    return new PricingAdvice(
        recommendedPrice, direction, confidence, reasoning, SuggestionSource.RULE_FALLBACK, reason);
  }
}

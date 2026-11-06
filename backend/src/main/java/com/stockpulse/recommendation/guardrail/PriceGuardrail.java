package com.stockpulse.recommendation.guardrail;

import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;

/**
 * Chain applied to every recommended price, whatever the strategy (ADR-6 seam). Sprint 2 adds
 * MarginFloorGuardrail (uses Product.marginFloor / costPrice) and CategoryCapGuardrail as beans.
 */
public interface PriceGuardrail {
  PricingAdvice apply(PricingAdvice advice, CommerceContext context);
}

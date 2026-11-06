package com.stockpulse.recommendation.strategy;

/**
 * Pricing contract shared by HTTP and async callers (ADR-2). A new strategy (e.g. Sprint 2
 * CompetitorAwareStrategy) only implements this and is registered as a bean (ADR-3, ADR-6).
 */
public interface PricingStrategy {
  /** Registry key, e.g. "rule" or "ai". */
  String key();

  PricingAdvice advise(CommerceContext context);
}

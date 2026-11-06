package com.stockpulse.recommendation.ai;

import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.PricingStrategy;
import com.stockpulse.recommendation.strategy.rule.RulePricingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The "ai" pricing strategy (ADR-3 key). Decorates the pure AI advisor with the rule strategy: any
 * AI failure yields a rule suggestion with source RULE_FALLBACK + fallbackReason, never a dropped
 * suggestion (BR-3, ADR-4).
 */
@Component
public class AiPricingStrategy implements PricingStrategy {
  public static final String KEY = "ai";
  private final AiAdvisor advisor;
  private final RulePricingStrategy rule;
  private final MeterRegistry meters;

  public AiPricingStrategy(AiAdvisor advisor, RulePricingStrategy rule, MeterRegistry meters) {
    this.advisor = advisor;
    this.rule = rule;
    this.meters = meters;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public PricingAdvice advise(CommerceContext ctx) {
    return AiFallbackSupport.adviseWithFallback(
        "pricing",
        ctx,
        advisor,
        () -> advisor.pricing(ctx),
        reason -> rule.advise(ctx).asFallback(reason),
        meters);
  }
}

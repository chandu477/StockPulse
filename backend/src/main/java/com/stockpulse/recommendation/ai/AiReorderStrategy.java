package com.stockpulse.recommendation.ai;

import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.ReorderAdvice;
import com.stockpulse.recommendation.strategy.ReorderStrategy;
import com.stockpulse.recommendation.strategy.rule.RuleReorderStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** The "ai" reorder strategy with rule fallback (ADR-2: independent of pricing). */
@Component
public class AiReorderStrategy implements ReorderStrategy {
  public static final String KEY = "ai";
  private final AiAdvisor advisor;
  private final RuleReorderStrategy rule;
  private final MeterRegistry meters;

  public AiReorderStrategy(AiAdvisor advisor, RuleReorderStrategy rule, MeterRegistry meters) {
    this.advisor = advisor;
    this.rule = rule;
    this.meters = meters;
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public ReorderAdvice advise(CommerceContext ctx) {
    return AiFallbackSupport.adviseWithFallback(
        "reorder",
        ctx,
        advisor,
        () -> advisor.reorder(ctx),
        reason -> rule.advise(ctx).asFallback(reason),
        meters);
  }
}

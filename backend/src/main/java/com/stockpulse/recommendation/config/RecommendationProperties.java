package com.stockpulse.recommendation.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables under stockpulse.* (plan §C.5, §C.9, Q-12, Q-18). */
@ConfigurationProperties(prefix = "stockpulse")
public record RecommendationProperties(
    Trigger trigger, Pricing pricing, Reorder reorder, Strategy strategy) {

  public record Trigger(BigDecimal spikeMultiplier, BigDecimal ruleVelocityMultiplier) {}

  public record Pricing(BigDecimal minRatio, BigDecimal maxRatio) {}

  public record Reorder(int maxQuantity, int coverMultiplier, int defaultLeadTimeDays) {}

  public record Strategy(String defaultPricing, String defaultReorder) {}
}

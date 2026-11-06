package com.stockpulse.recommendation.strategy.rule;

import com.stockpulse.entity.Direction;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.PricingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * FR-3: stock below threshold -> +10%; velocity above 2x peer average -> +5%; otherwise HOLD.
 * Deterministic, no external dependencies: the fallback for every AI failure.
 */
@Component
public class RulePricingStrategy implements PricingStrategy {
  public static final String KEY = "rule";
  private static final BigDecimal LOW_STOCK_UPLIFT = new BigDecimal("1.10");
  private static final BigDecimal HIGH_DEMAND_UPLIFT = new BigDecimal("1.05");
  private final BigDecimal velocityMultiplier;

  public RulePricingStrategy(RecommendationProperties props) {
    this.velocityMultiplier = props.trigger().ruleVelocityMultiplier();
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public PricingAdvice advise(CommerceContext ctx) {
    ProductView p = ctx.product();
    BigDecimal price = p.currentPrice();
    if (p.stockLevel() < p.reorderThreshold()) {
      return increase(
          price.multiply(LOW_STOCK_UPLIFT),
          0.70,
          "Rule: stock %d is below the reorder threshold %d, so raise the price 10%% to slow sales and protect the remaining units until replenishment arrives."
              .formatted(p.stockLevel(), p.reorderThreshold()));
    }
    BigDecimal limit = ctx.peers().averageVelocity().multiply(velocityMultiplier);
    if (ctx.peers().hasSignal() && BigDecimal.valueOf(p.demandVelocity()).compareTo(limit) > 0) {
      return increase(
          price.multiply(HIGH_DEMAND_UPLIFT),
          0.65,
          "Rule: demand velocity %d is above %sx the %s peer average (%s), so capture demand with a modest 5%% increase."
              .formatted(
                  p.demandVelocity(),
                  velocityMultiplier.stripTrailingZeros().toPlainString(),
                  p.category(),
                  ctx.peers().averageVelocity().toPlainString()));
    }
    return new PricingAdvice(
        price,
        Direction.HOLD,
        0.60,
        "Rule: stock %d is at or above the threshold %d and demand is in line with peers, so hold the price."
            .formatted(p.stockLevel(), p.reorderThreshold()),
        SuggestionSource.RULE,
        null);
  }

  private static PricingAdvice increase(BigDecimal raw, double confidence, String reasoning) {
    return new PricingAdvice(
        raw.setScale(2, RoundingMode.HALF_UP),
        Direction.INCREASE,
        confidence,
        reasoning,
        SuggestionSource.RULE,
        null);
  }
}

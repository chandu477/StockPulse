package com.stockpulse.recommendation.guardrail;

import com.stockpulse.entity.Direction;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Clamps any price into [min-ratio, max-ratio] x current price (Q-12) and fixes direction. */
@Component
@Order(0)
public class BoundsGuardrail implements PriceGuardrail {
  private final BigDecimal minRatio;
  private final BigDecimal maxRatio;

  public BoundsGuardrail(RecommendationProperties props) {
    this.minRatio = props.pricing().minRatio();
    this.maxRatio = props.pricing().maxRatio();
  }

  @Override
  public PricingAdvice apply(PricingAdvice advice, CommerceContext ctx) {
    BigDecimal current = ctx.product().currentPrice();
    BigDecimal min = current.multiply(minRatio).setScale(2, RoundingMode.HALF_UP);
    BigDecimal max = current.multiply(maxRatio).setScale(2, RoundingMode.HALF_UP);
    BigDecimal price = advice.recommendedPrice().max(min).min(max);
    String reasoning = advice.reasoning();
    if (price.compareTo(advice.recommendedPrice()) != 0) {
      reasoning += " [Guardrail: price clamped to the allowed range %s-%s.]".formatted(min, max);
    }
    return new PricingAdvice(
        price,
        directionOf(current, price),
        advice.confidence(),
        reasoning,
        advice.source(),
        advice.fallbackReason());
  }

  static Direction directionOf(BigDecimal current, BigDecimal recommended) {
    int cmp = recommended.compareTo(current);
    if (cmp > 0) {
      return Direction.INCREASE;
    }
    return cmp < 0 ? Direction.DECREASE : Direction.HOLD;
  }
}

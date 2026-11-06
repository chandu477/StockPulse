package com.stockpulse.recommendation.strategy.rule;

import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.ReorderAdvice;
import com.stockpulse.recommendation.strategy.ReorderStrategy;
import org.springframework.stereotype.Component;

/** FR-4: quantity = threshold x 3 - stock, minimum 1 (capped at the configured maximum). */
@Component
public class RuleReorderStrategy implements ReorderStrategy {
  public static final String KEY = "rule";
  private final int coverMultiplier;
  private final int maxQuantity;
  private final int leadTimeDays;

  public RuleReorderStrategy(RecommendationProperties props) {
    this.coverMultiplier = props.reorder().coverMultiplier();
    this.maxQuantity = props.reorder().maxQuantity();
    this.leadTimeDays = props.reorder().defaultLeadTimeDays();
  }

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public ReorderAdvice advise(CommerceContext ctx) {
    ProductView p = ctx.product();
    int target = p.reorderThreshold() * coverMultiplier;
    int quantity = Math.min(maxQuantity, Math.max(1, target - p.stockLevel()));
    return new ReorderAdvice(
        quantity,
        leadTimeDays,
        0.60,
        "Rule: restock to %dx the reorder threshold (%d units). Current stock is %d, so order %d."
            .formatted(coverMultiplier, target, p.stockLevel(), quantity),
        SuggestionSource.RULE,
        null);
  }
}

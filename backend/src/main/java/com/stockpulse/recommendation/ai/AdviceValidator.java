package com.stockpulse.recommendation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockpulse.entity.Direction;
import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.ReorderAdvice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Validates model output before it can become a suggestion (ADR-4). Anything absurd ($0, $999,999,
 * HOLD with a changed price, 1.5 units, confidence 1.7) is rejected -> rule fallback.
 */
@Component
public class AdviceValidator {
  private static final int MAX_REASONING = 2000;
  private static final int MAX_LEAD_TIME_DAYS = 60;
  private final RecommendationProperties props;

  public AdviceValidator(RecommendationProperties props) {
    this.props = props;
  }

  public PricingAdvice pricing(JsonNode json, CommerceContext ctx) {
    BigDecimal current = ctx.product().currentPrice();
    BigDecimal price = decimal(json, "recommendedPrice").setScale(2, RoundingMode.HALF_UP);
    BigDecimal min = current.multiply(props.pricing().minRatio()).setScale(2, RoundingMode.HALF_UP);
    BigDecimal max = current.multiply(props.pricing().maxRatio()).setScale(2, RoundingMode.HALF_UP);
    if (price.signum() <= 0 || price.compareTo(min) < 0 || price.compareTo(max) > 0) {
      throw bounds("recommendedPrice %s outside %s-%s".formatted(price, min, max));
    }
    Direction direction = direction(json);
    int cmp = price.compareTo(current);
    boolean consistent =
        switch (direction) {
          case INCREASE -> cmp > 0;
          case DECREASE -> cmp < 0;
          case HOLD -> cmp == 0;
        };
    if (!consistent) {
      throw bounds("direction %s inconsistent with %s -> %s".formatted(direction, current, price));
    }
    return new PricingAdvice(
        price, direction, confidence(json), reasoning(json), SuggestionSource.AI, null);
  }

  public ReorderAdvice reorder(JsonNode json) {
    JsonNode q = json.get("recommendedQuantity");
    if (q == null || !q.isIntegralNumber()) {
      throw bounds("recommendedQuantity must be an integer");
    }
    long quantity = q.asLong();
    if (quantity < 1 || quantity > props.reorder().maxQuantity()) {
      throw bounds(
          "recommendedQuantity %d outside 1-%d".formatted(quantity, props.reorder().maxQuantity()));
    }
    int leadTime = props.reorder().defaultLeadTimeDays();
    JsonNode lt = json.get("leadTimeDays");
    if (lt != null && !lt.isNull()) {
      if (!lt.isIntegralNumber() || lt.asInt() < 1 || lt.asInt() > MAX_LEAD_TIME_DAYS) {
        throw bounds("leadTimeDays must be an integer 1-" + MAX_LEAD_TIME_DAYS);
      }
      leadTime = lt.asInt();
    }
    return new ReorderAdvice(
        (int) quantity, leadTime, confidence(json), reasoning(json), SuggestionSource.AI, null);
  }

  private static BigDecimal decimal(JsonNode json, String field) {
    JsonNode n = json.get(field);
    if (n == null || !n.isNumber()) {
      throw bounds(field + " must be a number");
    }
    return n.decimalValue();
  }

  private static Direction direction(JsonNode json) {
    JsonNode n = json.get("direction");
    if (n == null || !n.isTextual()) {
      throw bounds("direction missing");
    }
    try {
      return Direction.valueOf(n.asText().trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw bounds("unknown direction " + n.asText());
    }
  }

  private static double confidence(JsonNode json) {
    JsonNode n = json.get("confidence");
    if (n == null || !n.isNumber() || n.asDouble() < 0 || n.asDouble() > 1) {
      throw bounds("confidence must be a number in [0,1]");
    }
    return n.asDouble();
  }

  private static String reasoning(JsonNode json) {
    JsonNode n = json.get("reasoning");
    if (n == null || !n.isTextual() || n.asText().isBlank()) {
      throw bounds("reasoning missing");
    }
    String text = n.asText().strip();
    return text.length() > MAX_REASONING ? text.substring(0, MAX_REASONING) : text;
  }

  private static AiAdviceException bounds(String message) {
    return new AiAdviceException(FallbackReason.OUT_OF_BOUNDS, message);
  }
}

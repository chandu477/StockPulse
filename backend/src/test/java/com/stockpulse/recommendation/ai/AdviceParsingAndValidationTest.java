package com.stockpulse.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.Category;
import com.stockpulse.entity.Direction;
import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdviceParsingAndValidationTest {
  static final RecommendationProperties PROPS =
      new RecommendationProperties(
          new RecommendationProperties.Trigger(new BigDecimal("3.0"), new BigDecimal("2.0")),
          new RecommendationProperties.Pricing(new BigDecimal("0.5"), new BigDecimal("2.0")),
          new RecommendationProperties.Reorder(10000, 3, 7),
          new RecommendationProperties.Strategy("rule", "rule"));

  private final AdviceParser parser = new AdviceParser(new ObjectMapper());
  private final AdviceValidator validator = new AdviceValidator(PROPS);
  private final CommerceContext ctx =
      new CommerceContext(
          new ProductView(
              "P", "S", "N", Category.HOME, new BigDecimal("50.00"), 5, 10, 3, null, null, null),
          TriggerReason.INVENTORY_LOW,
          PeerStats.NONE);

  private FallbackReason reasonOf(Runnable r) {
    try {
      r.run();
    } catch (AiAdviceException e) {
      return e.reason();
    }
    throw new AssertionError("expected AiAdviceException");
  }

  @Test
  void acceptsValidPricingEvenInsideCodeFencesAndProse() {
    var advice =
        validator.pricing(
            parser.parse(
                "Sure!\n```json\n{\"recommendedPrice\": 55.0, \"direction\": \"increase\","
                    + " \"confidence\": 0.8, \"reasoning\": \"Protect stock.\"}\n```"),
            ctx);
    assertThat(advice.recommendedPrice()).isEqualByComparingTo("55.00");
    assertThat(advice.direction()).isEqualTo(Direction.INCREASE);
    assertThat(advice.source()).isEqualTo(SuggestionSource.AI);
  }

  @Test
  void rejectsMissingOrBrokenJson() {
    assertThat(reasonOf(() -> parser.parse("raise the price")))
        .isEqualTo(FallbackReason.INVALID_JSON);
    assertThat(reasonOf(() -> parser.parse("{\"a\": }"))).isEqualTo(FallbackReason.INVALID_JSON);
    assertThat(reasonOf(() -> parser.parse("  "))).isEqualTo(FallbackReason.EMPTY_RESPONSE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"recommendedPrice\": 0, \"direction\": \"DECREASE\", \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 999999, \"direction\": \"INCREASE\", \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 52, \"direction\": \"HOLD\", \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 45, \"direction\": \"INCREASE\", \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 55, \"direction\": \"INCREASE\", \"confidence\": 1.7, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 55, \"direction\": \"INCREASE\", \"confidence\": 0.5, \"reasoning\": \" \"}",
        "{\"recommendedPrice\": \"55\", \"direction\": \"INCREASE\", \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedPrice\": 55, \"direction\": \"SIDEWAYS\", \"confidence\": 0.5, \"reasoning\": \"x\"}"
      })
  void rejectsAbsurdPricing(String json) {
    assertThat(reasonOf(() -> validator.pricing(parser.parse(json), ctx)))
        .isEqualTo(FallbackReason.OUT_OF_BOUNDS);
  }

  @Test
  void acceptsHoldAtCurrentPrice() {
    var advice =
        validator.pricing(
            parser.parse(
                "{\"recommendedPrice\": 50.00, \"direction\": \"HOLD\", \"confidence\": 0.6,"
                    + " \"reasoning\": \"Signals conflict.\"}"),
            ctx);
    assertThat(advice.direction()).isEqualTo(Direction.HOLD);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"recommendedQuantity\": 0, \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedQuantity\": -5, \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedQuantity\": 1.5, \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedQuantity\": 20000, \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"recommendedQuantity\": 10, \"leadTimeDays\": 0, \"confidence\": 0.5, \"reasoning\": \"x\"}",
        "{\"confidence\": 0.5, \"reasoning\": \"x\"}"
      })
  void rejectsInvalidReorder(String json) {
    assertThat(reasonOf(() -> validator.reorder(parser.parse(json))))
        .isEqualTo(FallbackReason.OUT_OF_BOUNDS);
  }

  @Test
  void reorderDefaultsLeadTimeWhenMissing() {
    var advice =
        validator.reorder(
            parser.parse(
                "{\"recommendedQuantity\": 150, \"confidence\": 0.78, \"reasoning\": \"ok\"}"));
    assertThat(advice.recommendedQuantity()).isEqualTo(150);
    assertThat(advice.leadTimeDays()).isEqualTo(7);
  }

  @Test
  void unknownExceptionTypeIsNotSwallowed() {
    assertThatThrownBy(() -> validator.reorder(null)).isInstanceOf(NullPointerException.class);
  }
}

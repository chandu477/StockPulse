package com.stockpulse.recommendation;

import static com.stockpulse.recommendation.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockpulse.entity.Direction;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.guardrail.BoundsGuardrail;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BoundsGuardrailTest {
  private final BoundsGuardrail guardrail = new BoundsGuardrail(Fixtures.props());
  private final CommerceContext ctx =
      new CommerceContext(product("50.00", 5, 10, 0), TriggerReason.MANUAL, PeerStats.NONE);

  private static PricingAdvice advice(String price) {
    return new PricingAdvice(
        new BigDecimal(price), Direction.INCREASE, 0.9, "x", SuggestionSource.AI, null);
  }

  @Test
  void clampsAbsurdHighPrice() {
    var out = guardrail.apply(advice("999999"), ctx);
    assertThat(out.recommendedPrice()).isEqualByComparingTo("100.00");
    assertThat(out.reasoning()).contains("Guardrail");
  }

  @Test
  void clampsZeroPriceAndFixesDirection() {
    var out = guardrail.apply(advice("0"), ctx);
    assertThat(out.recommendedPrice()).isEqualByComparingTo("25.00");
    assertThat(out.direction()).isEqualTo(Direction.DECREASE);
  }

  @Test
  void leavesSanePriceAlone() {
    var out = guardrail.apply(advice("55.00"), ctx);
    assertThat(out.recommendedPrice()).isEqualByComparingTo("55.00");
    assertThat(out.reasoning()).isEqualTo("x");
  }
}

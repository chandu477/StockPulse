package com.stockpulse.recommendation;

import static com.stockpulse.recommendation.Fixtures.peers;
import static com.stockpulse.recommendation.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockpulse.entity.Direction;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.rule.RulePricingStrategy;
import com.stockpulse.recommendation.strategy.rule.RuleReorderStrategy;
import org.junit.jupiter.api.Test;

class RuleStrategiesTest {
  private final RulePricingStrategy pricing = new RulePricingStrategy(Fixtures.props());
  private final RuleReorderStrategy reorder = new RuleReorderStrategy(Fixtures.props());

  private static CommerceContext ctx(com.stockpulse.entity.ProductView p, PeerStats s) {
    return new CommerceContext(p, TriggerReason.INVENTORY_LOW, s);
  }

  @Test
  void lowStockRaisesTenPercent() {
    var advice = pricing.advise(ctx(product("24.99", 7, 15, 0), PeerStats.NONE));
    assertThat(advice.recommendedPrice()).isEqualByComparingTo("27.49");
    assertThat(advice.direction()).isEqualTo(Direction.INCREASE);
    assertThat(advice.source()).isEqualTo(SuggestionSource.RULE);
    assertThat(advice.reasoning()).contains("below the reorder threshold");
  }

  @Test
  void highVelocityRaisesFivePercent() {
    var advice = pricing.advise(ctx(product("100.00", 50, 10, 15), peers("7", 2)));
    assertThat(advice.recommendedPrice()).isEqualByComparingTo("105.00");
  }

  @Test
  void otherwiseHold() {
    var advice = pricing.advise(ctx(product("100.00", 50, 10, 14), peers("7", 2)));
    assertThat(advice.direction()).isEqualTo(Direction.HOLD);
    assertThat(advice.recommendedPrice()).isEqualByComparingTo("100.00");
  }

  @Test
  void reorderToThreeTimesThreshold() {
    var advice = reorder.advise(ctx(product("10", 7, 15, 0), PeerStats.NONE));
    assertThat(advice.recommendedQuantity()).isEqualTo(38);
    assertThat(advice.leadTimeDays()).isEqualTo(7);
  }

  @Test
  void reorderIsAtLeastOne() {
    var advice = reorder.advise(ctx(product("10", 500, 15, 0), PeerStats.NONE));
    assertThat(advice.recommendedQuantity()).isEqualTo(1);
  }
}

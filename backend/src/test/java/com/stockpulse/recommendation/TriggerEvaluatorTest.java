package com.stockpulse.recommendation;

import static com.stockpulse.recommendation.Fixtures.peers;
import static com.stockpulse.recommendation.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.service.TriggerEvaluator;
import org.junit.jupiter.api.Test;

class TriggerEvaluatorTest {
  private final TriggerEvaluator evaluator = new TriggerEvaluator(Fixtures.props());

  @Test
  void inventoryLowWhenStockBelowThreshold() {
    assertThat(evaluator.evaluate(product("10", 14, 15, 0), PeerStats.NONE))
        .containsExactly(TriggerReason.INVENTORY_LOW);
  }

  @Test
  void stockEqualToThresholdIsNotLow() {
    assertThat(evaluator.evaluate(product("10", 15, 15, 0), PeerStats.NONE)).isEmpty();
  }

  @Test
  void spikeUsesPeerAverageExcludingSelf() {
    // Seed PRD-008: velocity 15 vs peers PRD-003 (12) and PRD-004 (2) -> avg 7 -> limit 21.
    assertThat(evaluator.isSpike(product("10", 50, 12, 21), peers("7", 2))).isFalse();
    assertThat(evaluator.isSpike(product("10", 50, 12, 22), peers("7", 2))).isTrue();
  }

  @Test
  void noSpikeWithoutPeersOrWithZeroAverage() {
    assertThat(evaluator.isSpike(product("10", 50, 12, 1000), PeerStats.NONE)).isFalse();
    assertThat(evaluator.isSpike(product("10", 50, 12, 1000), peers("0", 3))).isFalse();
  }

  @Test
  void bothTriggersCanFireTogether() {
    assertThat(evaluator.evaluate(product("10", 5, 12, 100), peers("7", 2)))
        .containsExactly(TriggerReason.INVENTORY_LOW, TriggerReason.DEMAND_SPIKE);
  }
}

package com.stockpulse.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockpulse.entity.TriggerReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrimaryTriggerTest {
  @Test
  void demandSpikeWinsWhenBothFire() {
    assertThat(
            RecommendationRequestHandler.primary(
                List.of(TriggerReason.INVENTORY_LOW, TriggerReason.DEMAND_SPIKE)))
        .contains(TriggerReason.DEMAND_SPIKE);
    assertThat(RecommendationRequestHandler.primary(List.of(TriggerReason.INVENTORY_LOW)))
        .contains(TriggerReason.INVENTORY_LOW);
    assertThat(RecommendationRequestHandler.primary(List.of())).isEmpty();
  }
}

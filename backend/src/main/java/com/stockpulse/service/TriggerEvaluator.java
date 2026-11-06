package com.stockpulse.service;

import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.config.RecommendationProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Level-triggered evaluation (Q-7, plan §C.5). INVENTORY_LOW when stock is below the threshold;
 * DEMAND_SPIKE when velocity exceeds multiplier x the peer average, where peers exclude the
 * product.
 */
@Component
public class TriggerEvaluator {
  private final BigDecimal spikeMultiplier;

  public TriggerEvaluator(RecommendationProperties props) {
    this.spikeMultiplier = props.trigger().spikeMultiplier();
  }

  public List<TriggerReason> evaluate(ProductView product, PeerStats peers) {
    List<TriggerReason> fired = new ArrayList<>(2);
    if (product.stockLevel() < product.reorderThreshold()) {
      fired.add(TriggerReason.INVENTORY_LOW);
    }
    if (isSpike(product, peers)) {
      fired.add(TriggerReason.DEMAND_SPIKE);
    }
    return fired;
  }

  public boolean isSpike(ProductView product, PeerStats peers) {
    if (!peers.hasSignal()) {
      return false;
    }
    BigDecimal limit = peers.averageVelocity().multiply(spikeMultiplier);
    return BigDecimal.valueOf(product.demandVelocity()).compareTo(limit) > 0;
  }

  public BigDecimal spikeMultiplier() {
    return spikeMultiplier;
  }
}

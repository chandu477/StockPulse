package com.stockpulse.entity;

import java.math.BigDecimal;

/**
 * Demand velocity of the other products in the same category, excluding the product itself (plan
 * §C.5). Including the product would make the spike condition impossible to reach.
 */
public record PeerStats(BigDecimal averageVelocity, long peerCount) {
  public static final PeerStats NONE = new PeerStats(BigDecimal.ZERO, 0);

  public boolean hasSignal() {
    return peerCount > 0 && averageVelocity.signum() > 0;
  }
}

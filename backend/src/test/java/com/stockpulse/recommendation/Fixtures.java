package com.stockpulse.recommendation;

import com.stockpulse.entity.Category;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.ProductView;
import com.stockpulse.recommendation.config.RecommendationProperties;
import java.math.BigDecimal;

final class Fixtures {
  private Fixtures() {}

  static RecommendationProperties props() {
    return new RecommendationProperties(
        new RecommendationProperties.Trigger(new BigDecimal("3.0"), new BigDecimal("2.0")),
        new RecommendationProperties.Pricing(new BigDecimal("0.5"), new BigDecimal("2.0")),
        new RecommendationProperties.Reorder(10000, 3, 7),
        new RecommendationProperties.Strategy("rule", "rule"));
  }

  static ProductView product(String price, int stock, int threshold, int velocity) {
    return new ProductView(
        "PRD-T",
        "SKU-T",
        "Test",
        Category.APPAREL,
        new BigDecimal(price),
        stock,
        threshold,
        velocity,
        null,
        null,
        null);
  }

  static PeerStats peers(String avg, long count) {
    return new PeerStats(new BigDecimal(avg), count);
  }
}

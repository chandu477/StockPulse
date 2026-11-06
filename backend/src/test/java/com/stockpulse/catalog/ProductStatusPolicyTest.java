package com.stockpulse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockpulse.entity.ProductStatus;
import com.stockpulse.entity.ProductStatusPolicy;
import org.junit.jupiter.api.Test;

class ProductStatusPolicyTest {
  @Test
  void outOfStockWinsOverPending() {
    assertThat(ProductStatusPolicy.derive(0, true)).isEqualTo(ProductStatus.OUT_OF_STOCK);
    assertThat(ProductStatusPolicy.derive(0, false)).isEqualTo(ProductStatus.OUT_OF_STOCK);
  }

  @Test
  void pendingSuggestionMeansReview() {
    assertThat(ProductStatusPolicy.derive(5, true)).isEqualTo(ProductStatus.PRICE_REVIEW_PENDING);
  }

  @Test
  void otherwiseActive() {
    assertThat(ProductStatusPolicy.derive(5, false)).isEqualTo(ProductStatus.ACTIVE);
  }
}

package com.stockpulse.entity;

import java.util.Collection;
import java.util.Map;

/**
 * Port implemented by the recommendation module so catalog can derive status and show counts
 * without depending on recommendation (plan §C.3).
 */
public interface PendingSuggestionQuery {
  record PendingCounts(long pricing, long reorder) {
    public static final PendingCounts NONE = new PendingCounts(0, 0);

    public boolean any() {
      return pricing + reorder > 0;
    }
  }

  PendingCounts countsFor(String productId);

  Map<String, PendingCounts> countsFor(Collection<String> productIds);
}

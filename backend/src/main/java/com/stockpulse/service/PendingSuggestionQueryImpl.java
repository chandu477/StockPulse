package com.stockpulse.service;

import com.stockpulse.entity.PendingSuggestionQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class PendingSuggestionQueryImpl implements PendingSuggestionQuery {
  private final PricingSuggestionRepository pricing;
  private final ReorderSuggestionRepository reorder;

  PendingSuggestionQueryImpl(
      PricingSuggestionRepository pricing, ReorderSuggestionRepository reorder) {
    this.pricing = pricing;
    this.reorder = reorder;
  }

  @Override
  public PendingCounts countsFor(String productId) {
    return countsFor(List.of(productId)).getOrDefault(productId, PendingCounts.NONE);
  }

  @Override
  public Map<String, PendingCounts> countsFor(Collection<String> productIds) {
    Map<String, PendingCounts> result = new HashMap<>();
    if (productIds.isEmpty()) {
      return result;
    }
    for (Object[] row : pricing.countPendingByProduct(productIds)) {
      String id = (String) row[0];
      long n = ((Number) row[1]).longValue();
      result.merge(id, new PendingCounts(n, 0), PendingSuggestionQueryImpl::sum);
    }
    for (Object[] row : reorder.countPendingByProduct(productIds)) {
      String id = (String) row[0];
      long n = ((Number) row[1]).longValue();
      result.merge(id, new PendingCounts(0, n), PendingSuggestionQueryImpl::sum);
    }
    return result;
  }

  private static PendingCounts sum(PendingCounts a, PendingCounts b) {
    return new PendingCounts(a.pricing() + b.pricing(), a.reorder() + b.reorder());
  }
}

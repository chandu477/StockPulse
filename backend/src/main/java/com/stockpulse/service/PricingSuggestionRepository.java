package com.stockpulse.service;

import com.stockpulse.entity.PricingSuggestion;
import com.stockpulse.entity.SuggestionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, Long> {
  Optional<PricingSuggestion> findByPendingKey(String pendingKey);

  List<PricingSuggestion> findByProductIdAndStatus(String productId, SuggestionStatus status);

  @Query(
      "select s from PricingSuggestion s where (:status is null or s.status = :status)"
          + " and (:productId is null or s.productId = :productId) order by s.createdAt desc, s.id desc")
  List<PricingSuggestion> search(
      @Param("status") SuggestionStatus status, @Param("productId") String productId);

  @Query(
      "select s.productId, count(s) from PricingSuggestion s"
          + " where s.status = com.stockpulse.entity.SuggestionStatus.PENDING"
          + " and s.productId in :ids group by s.productId")
  List<Object[]> countPendingByProduct(@Param("ids") Collection<String> ids);

  List<PricingSuggestion> findByProductIdAndStatusOrderByDecidedAtAsc(
      String productId, SuggestionStatus status);
}

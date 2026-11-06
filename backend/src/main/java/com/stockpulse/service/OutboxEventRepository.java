package com.stockpulse.service;

import com.stockpulse.entity.OutboxEvent;
import com.stockpulse.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      "select o.id from OutboxEvent o where o.status = com.stockpulse.entity.OutboxStatus.PENDING"
          + " and o.createdAt < :before order by o.createdAt")
  List<UUID> findPendingIdsCreatedBefore(@Param("before") Instant before);

  long countByStatus(OutboxStatus status);
}

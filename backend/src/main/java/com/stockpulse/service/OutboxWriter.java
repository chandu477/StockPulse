package com.stockpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.OutboxEvent;
import com.stockpulse.entity.Product;
import com.stockpulse.shared.web.TraceIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the outbox row inside the caller's transaction (ADR-5: no dual write). */
@Component
public class OutboxWriter {
  private final OutboxEventRepository repository;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public OutboxWriter(
      OutboxEventRepository repository,
      ObjectMapper mapper,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.mapper = mapper;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID inventoryChanged(Product product, String cause) {
    var payload =
        new InventoryChangedPayload(
            product.getId(), cause, product.getStockLevel(), product.getDemandVelocity());
    UUID id = UUID.randomUUID();
    repository.save(
        new OutboxEvent(
            id,
            product.getId(),
            OutboxEvent.INVENTORY_CHANGED,
            toJson(payload),
            MDC.get(TraceIdFilter.MDC_KEY),
            Instant.now(clock)));
    events.publishEvent(new OutboxRecorded(id));
    return id;
  }

  private String toJson(InventoryChangedPayload payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialise outbox payload", e);
    }
  }
}

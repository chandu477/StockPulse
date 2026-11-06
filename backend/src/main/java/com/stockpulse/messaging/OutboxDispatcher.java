package com.stockpulse.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.OutboxEvent;
import com.stockpulse.entity.OutboxStatus;
import com.stockpulse.service.InventoryChangedPayload;
import com.stockpulse.service.InventorySignal;
import com.stockpulse.service.OutboxEventRepository;
import com.stockpulse.shared.logging.LogEvents;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared outbox bookkeeping for both messaging modes (ADR-5). */
public abstract class OutboxDispatcher {
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final OutboxEventRepository outbox;
  protected final TransactionTemplate tx;
  protected final ObjectMapper mapper;
  protected final Clock clock;
  private final int maxAttempts;

  protected OutboxDispatcher(
      OutboxEventRepository outbox,
      TransactionTemplate tx,
      ObjectMapper mapper,
      Clock clock,
      int maxAttempts) {
    this.outbox = outbox;
    this.tx = tx;
    this.mapper = mapper;
    this.clock = clock;
    this.maxAttempts = maxAttempts;
  }

  /** Delivers one committed outbox row; marks it PUBLISHED or records the failed attempt. */
  protected void dispatch(UUID id) {
    OutboxEvent row = outbox.findById(id).orElse(null);
    if (row == null || row.getStatus() != OutboxStatus.PENDING) {
      return;
    }
    try {
      deliver(toSignal(row));
      tx.executeWithoutResult(s ->
          outbox
              .findById(id)
              .ifPresent(r -> {
                if (r.getStatus() == OutboxStatus.PENDING) {
                  r.markPublished(Instant.now(clock));
                }
              }));
      log.atDebug()
          .addKeyValue("event", LogEvents.OUTBOX_PUBLISHED)
          .addKeyValue("outboxId", id.toString())
          .log("Outbox {} delivered", id);
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s ->
          outbox
              .findById(id)
              .ifPresent(r -> r.markAttemptFailed(e.getClass().getSimpleName(), maxAttempts)));
      log.atError()
          .addKeyValue("event", LogEvents.OUTBOX_PUBLISH_FAILED)
          .addKeyValue("outboxId", id.toString())
          .setCause(e)
          .log("Outbox {} delivery failed; will retry until {} attempts", id, maxAttempts);
    }
  }

  protected abstract void deliver(InventorySignal signal);

  protected InventorySignal toSignal(OutboxEvent row) {
    try {
      InventoryChangedPayload p = mapper.readValue(row.getPayload(), InventoryChangedPayload.class);
      return new InventorySignal(
          row.getId().toString(), p.productId(), p.cause(), row.getTraceId());
    } catch (IOException e) {
      throw new IllegalStateException("Unreadable outbox payload " + row.getId(), e);
    }
  }
}

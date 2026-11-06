package com.stockpulse.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.service.InventorySignal;
import com.stockpulse.service.OutboxEventRepository;
import com.stockpulse.service.OutboxRecorded;
import com.stockpulse.service.RecommendationRequestHandler;
import com.stockpulse.shared.config.MdcTaskDecorator;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Zero-setup messaging for the local H2 profile (ADR-5 "in-process" mode). The outbox row is still
 * written in the stock transaction; after commit it is handed to the same handler the RabbitMQ
 * listener uses, on a single background thread so the order request returns immediately. A sweep
 * re-delivers rows left PENDING (crash, handler failure). Not used with RabbitMQ.
 */
@Component
@ConditionalOnProperty(name = "stockpulse.messaging.mode", havingValue = "in-process")
public class InProcessOutboxDispatcher extends OutboxDispatcher implements DisposableBean {
  private final RecommendationRequestHandler handler;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "outbox-inprocess"));

  public InProcessOutboxDispatcher(
      OutboxEventRepository outbox,
      TransactionTemplate tx,
      ObjectMapper mapper,
      Clock clock,
      RecommendationRequestHandler handler,
      @Value("${stockpulse.outbox.max-attempts}") int maxAttempts) {
    super(outbox, tx, mapper, clock, maxAttempts);
    this.handler = handler;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommitted(OutboxRecorded event) {
    worker.execute(MdcTaskDecorator.wrap(() -> dispatch(event.outboxId())));
  }

  /** Safety net, not the trigger: only rows older than one sweep interval are retried. */
  @Scheduled(fixedDelayString = "${stockpulse.outbox.sweep-interval-ms}")
  public void sweep() {
    Instant before = Instant.now(clock).minusSeconds(5);
    for (var id : outbox.findPendingIdsCreatedBefore(before)) {
      worker.execute(() -> dispatch(id));
    }
  }

  @Override
  protected void deliver(InventorySignal signal) {
    handler.handle(signal);
  }

  @Override
  public void destroy() {
    worker.shutdownNow();
  }
}

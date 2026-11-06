package com.stockpulse.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.service.InventorySignal;
import com.stockpulse.service.OutboxEventRepository;
import com.stockpulse.service.OutboxRecorded;
import com.stockpulse.shared.config.MdcTaskDecorator;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox relay for RabbitMQ (ADR-5). Triggered right after the stock transaction commits; a 5 s
 * sweep re-publishes rows still PENDING (relay crash, broker down). A row becomes PUBLISHED only
 * after the broker confirms (publisher confirms + mandatory routing).
 */
@Component
@ConditionalOnProperty(name = "stockpulse.messaging.mode", havingValue = "rabbitmq")
public class RabbitOutboxRelay extends OutboxDispatcher implements DisposableBean {
  private static final long CONFIRM_TIMEOUT_SECONDS = 5;
  private final RabbitTemplate rabbit;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "outbox-relay"));

  public RabbitOutboxRelay(
      OutboxEventRepository outbox,
      TransactionTemplate tx,
      ObjectMapper mapper,
      Clock clock,
      RabbitTemplate rabbit,
      @Value("${stockpulse.outbox.max-attempts}") int maxAttempts) {
    super(outbox, tx, mapper, clock, maxAttempts);
    this.rabbit = rabbit;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommitted(OutboxRecorded event) {
    worker.execute(MdcTaskDecorator.wrap(() -> dispatch(event.outboxId())));
  }

  @Scheduled(fixedDelayString = "${stockpulse.outbox.sweep-interval-ms}")
  public void sweep() {
    Instant before = Instant.now(clock).minusSeconds(5);
    for (var id : outbox.findPendingIdsCreatedBefore(before)) {
      worker.execute(() -> dispatch(id));
    }
  }

  @Override
  protected void deliver(InventorySignal signal) {
    CorrelationData correlation = new CorrelationData(signal.messageId());
    rabbit.convertAndSend(
        RabbitMessagingConfig.EXCHANGE,
        RabbitMessagingConfig.ROUTING_KEY,
        signal,
        message -> {
          message.getMessageProperties().setMessageId(signal.messageId());
          message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          if (signal.traceId() != null) {
            message.getMessageProperties().setHeader("traceId", signal.traceId());
          }
          return message;
        },
        correlation);
    try {
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!confirm.isAck()) {
        throw new IllegalStateException("Broker nacked message: " + confirm.getReason());
      }
      if (correlation.getReturned() != null) {
        throw new IllegalStateException("Message unroutable: " + correlation.getReturned());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted waiting for broker confirm", e);
    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
      throw new IllegalStateException("No broker confirm", e);
    }
  }

  @Override
  public void destroy() {
    worker.shutdownNow();
  }
}

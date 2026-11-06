package com.stockpulse.service;

import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.ProcessedMessage;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.shared.logging.LogEvents;
import com.stockpulse.shared.web.TraceIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Agentic loop consumer (ADR-5). Same handler for both messaging modes: in-process (local) and
 * RabbitMQ. Observe (signal) -> reason (triggers + strategies) -> act (PENDING suggestions).
 * Idempotent: processed_messages skips redeliveries; pending_key prevents duplicate suggestions.
 */
@Component
public class RecommendationRequestHandler {
  private static final Logger log = LoggerFactory.getLogger(RecommendationRequestHandler.class);

  private final ProcessedMessageRepository processed;
  private final ProductService products;
  private final TriggerEvaluator evaluator;
  private final RecommendationService recommendations;
  private final TransactionTemplate tx;
  private final Clock clock;

  public RecommendationRequestHandler(
      ProcessedMessageRepository processed,
      ProductService products,
      TriggerEvaluator evaluator,
      RecommendationService recommendations,
      TransactionTemplate tx,
      Clock clock) {
    this.processed = processed;
    this.products = products;
    this.evaluator = evaluator;
    this.recommendations = recommendations;
    this.tx = tx;
    this.clock = clock;
  }

  public void handle(InventorySignal signal) {
    String previousTrace = MDC.get(TraceIdFilter.MDC_KEY);
    MDC.put(
        TraceIdFilter.MDC_KEY,
        signal.traceId() == null ? TraceIdFilter.newTraceId() : signal.traceId());
    try {
      process(signal);
    } finally {
      if (previousTrace == null) {
        MDC.remove(TraceIdFilter.MDC_KEY);
      } else {
        MDC.put(TraceIdFilter.MDC_KEY, previousTrace);
      }
    }
  }

  private void process(InventorySignal signal) {
    if (processed.existsById(signal.messageId())) {
      log.atInfo()
          .addKeyValue("event", LogEvents.MESSAGE_DUPLICATE_SKIPPED)
          .addKeyValue("messageId", signal.messageId())
          .log("Message {} already processed; skipped", signal.messageId());
      return;
    }
    ProductView view = products.view(signal.productId());
    PeerStats peers = products.peerStats(view);
    List<TriggerReason> triggers = evaluator.evaluate(view, peers);
    log.atInfo()
        .addKeyValue("event", LogEvents.TRIGGER_EVALUATED)
        .addKeyValue("productId", view.id())
        .addKeyValue("stockLevel", view.stockLevel())
        .addKeyValue("reorderThreshold", view.reorderThreshold())
        .addKeyValue("demandVelocity", view.demandVelocity())
        .addKeyValue("peerAvgVelocity", peers.averageVelocity())
        .addKeyValue("triggers", triggers.toString())
        .log("Triggers for {}: {}", view.id(), triggers);
    primary(triggers)
        .ifPresent(
            t -> recommendations.generate(view.id(), t, EnumSet.allOf(SuggestionType.class)));
    markProcessed(signal.messageId());
  }

  /**
   * One recommendation per product: when both triggers fire, DEMAND_SPIKE wins because its prompt
   * also weighs stock headroom, while the low-stock prompt would ignore the spike.
   */
  static java.util.Optional<TriggerReason> primary(List<TriggerReason> triggers) {
    if (triggers.contains(TriggerReason.DEMAND_SPIKE)) {
      return java.util.Optional.of(TriggerReason.DEMAND_SPIKE);
    }
    return triggers.stream().findFirst();
  }

  private void markProcessed(String messageId) {
    try {
      tx.executeWithoutResult(
          s -> processed.save(new ProcessedMessage(messageId, Instant.now(clock))));
    } catch (DataIntegrityViolationException concurrentDuplicate) {
      log.atInfo()
          .addKeyValue("event", LogEvents.MESSAGE_DUPLICATE_SKIPPED)
          .addKeyValue("messageId", messageId)
          .log("Message {} was processed concurrently", messageId);
    }
  }
}

package com.stockpulse.messaging;

import com.stockpulse.service.InventorySignal;
import com.stockpulse.service.RecommendationRequestHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumer side of the agentic loop (ADR-5). Delegates to the same idempotent handler as the
 * in-process mode. Exceptions trigger listener retries, then the dead-letter queue.
 */
@Component
@ConditionalOnProperty(name = "stockpulse.messaging.mode", havingValue = "rabbitmq")
public class RabbitRecommendationListener {
  private final RecommendationRequestHandler handler;

  public RabbitRecommendationListener(RecommendationRequestHandler handler) {
    this.handler = handler;
  }

  @RabbitListener(queues = RabbitMessagingConfig.QUEUE)
  public void onInventoryChanged(InventorySignal signal) {
    handler.handle(signal);
  }
}

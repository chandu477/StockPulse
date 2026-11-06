package com.stockpulse.messaging;

import com.stockpulse.shared.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology (ADR-5): topic exchange stockpulse.inventory -> durable queue
 * recommendation.requests (dead-letters to recommendation.dlx -> recommendation.dlq).
 */
@Configuration
@ConditionalOnProperty(name = "stockpulse.messaging.mode", havingValue = "rabbitmq")
public class RabbitMessagingConfig {
  public static final String EXCHANGE = "stockpulse.inventory";
  public static final String ROUTING_KEY = "inventory.changed";
  public static final String QUEUE = "recommendation.requests";
  public static final String DLX = "recommendation.dlx";
  public static final String DLQ = "recommendation.dlq";
  private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

  @Bean
  Declarables recommendationTopology() {
    TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
    DirectExchange dlx = new DirectExchange(DLX, true, false);
    Queue queue =
        QueueBuilder.durable(QUEUE)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", DLQ)
            .build();
    Queue dlq = QueueBuilder.durable(DLQ).build();
    Binding binding = BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(DLQ);
    return new Declarables(exchange, dlx, queue, dlq, binding, dlqBinding);
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /**
   * After the listener's retries (1 s, 2 s, 4 s) are exhausted: log MESSAGE_DEAD_LETTERED and
   * reject without requeue so the broker routes it to recommendation.dlq. AI failures never get
   * here; they already fell back to rules (ADR-4).
   */
  @Bean
  MessageRecoverer deadLetterRecoverer() {
    RejectAndDontRequeueRecoverer reject = new RejectAndDontRequeueRecoverer();
    return (message, cause) -> {
      log.atError()
          .addKeyValue("event", LogEvents.MESSAGE_DEAD_LETTERED)
          .addKeyValue("messageId", message.getMessageProperties().getMessageId())
          .setCause(cause)
          .log("Message dead-lettered to {}", DLQ);
      reject.recover(message, cause);
    };
  }
}

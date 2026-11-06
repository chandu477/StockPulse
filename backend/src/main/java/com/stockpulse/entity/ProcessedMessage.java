package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Idempotent-consumer record (ADR-5): a redelivered message with this id is skipped. */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {
  @Id
  @Column(name = "message_id")
  private String messageId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedMessage() {}

  public ProcessedMessage(String messageId, Instant processedAt) {
    this.messageId = messageId;
    this.processedAt = processedAt;
  }

  public String getMessageId() {
    return messageId;
  }
}

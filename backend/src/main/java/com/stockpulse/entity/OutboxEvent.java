package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Transactional outbox row (ADR-5). Written in the same transaction as the stock change. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  public static final String INVENTORY_CHANGED = "INVENTORY_CHANGED";

  @Id private UUID id;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String payload;

  @Column(name = "trace_id")
  private String traceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEvent() {}

  public OutboxEvent(
      UUID id,
      String aggregateId,
      String eventType,
      String payload,
      String traceId,
      Instant createdAt) {
    this.id = id;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.traceId = traceId;
    this.status = OutboxStatus.PENDING;
    this.createdAt = createdAt;
  }

  public void markPublished(Instant at) {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = at;
    this.lastError = null;
  }

  /** Records a failed attempt; FAILED once attempts reach maxAttempts (operator attention). */
  public void markAttemptFailed(String error, int maxAttempts) {
    this.attempts++;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    if (attempts >= maxAttempts) {
      this.status = OutboxStatus.FAILED;
    }
  }

  public UUID getId() {
    return id;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public String getTraceId() {
    return traceId;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }
}

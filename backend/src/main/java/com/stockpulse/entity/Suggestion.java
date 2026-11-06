package com.stockpulse.entity;

import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Common state machine for pricing and reorder suggestions: PENDING -> ACCEPTED | REJECTED |
 * SUPERSEDED. Terminal states are final. pendingKey (= product id) is set only while PENDING, so a
 * product has at most one PENDING suggestion of each type (BR-4, ADR-7 revision).
 */
@MappedSuperclass
public abstract class Suggestion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private String productId;

  @Column(nullable = false)
  private BigDecimal confidence;

  @Column(nullable = false)
  private String reasoning;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SuggestionStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_reason", nullable = false)
  private TriggerReason triggerReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SuggestionSource source;

  @Column(name = "strategy_key", nullable = false)
  private String strategyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "fallback_reason")
  private FallbackReason fallbackReason;

  @Column(name = "pending_key")
  private String pendingKey;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note")
  private String decisionNote;

  @Version private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Suggestion() {}

  protected Suggestion(
      String productId,
      double confidence,
      String reasoning,
      TriggerReason triggerReason,
      SuggestionSource source,
      String strategyKey,
      FallbackReason fallbackReason,
      Instant createdAt) {
    this.productId = productId;
    this.confidence = BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    this.reasoning = truncate(reasoning, 4000);
    this.status = SuggestionStatus.PENDING;
    this.triggerReason = triggerReason;
    this.source = source;
    this.strategyKey = strategyKey;
    this.fallbackReason = fallbackReason;
    this.pendingKey = pendingKey(productId);
    this.createdAt = createdAt;
  }

  public static String pendingKey(String productId) {
    return productId;
  }

  public void accept(String note, Instant at) {
    decide(SuggestionStatus.ACCEPTED, note, at);
  }

  public void reject(String note, Instant at) {
    decide(SuggestionStatus.REJECTED, note, at);
  }

  public void supersede(Instant at) {
    supersede("Superseded by another accepted suggestion.", at);
  }

  public void supersede(String note, Instant at) {
    decide(SuggestionStatus.SUPERSEDED, note, at);
  }

  private void decide(SuggestionStatus target, String note, Instant at) {
    if (status != SuggestionStatus.PENDING) {
      throw new ApiException(
          ErrorCode.NOT_PENDING, "Suggestion " + id + " is already " + status + ".");
    }
    this.status = target;
    this.decidedAt = at;
    this.decisionNote = note == null ? null : truncate(note, 500);
    this.pendingKey = null;
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  public boolean isPending() {
    return status == SuggestionStatus.PENDING;
  }

  public Long getId() {
    return id;
  }

  public String getProductId() {
    return productId;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getReasoning() {
    return reasoning;
  }

  public SuggestionStatus getStatus() {
    return status;
  }

  public TriggerReason getTriggerReason() {
    return triggerReason;
  }

  public SuggestionSource getSource() {
    return source;
  }

  public String getStrategyKey() {
    return strategyKey;
  }

  public FallbackReason getFallbackReason() {
    return fallbackReason;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

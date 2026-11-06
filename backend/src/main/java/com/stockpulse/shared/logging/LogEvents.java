package com.stockpulse.shared.logging;

/** Event catalogue (observability §4). Use these names in the "event" key of every log line. */
public final class LogEvents {
  public static final String HTTP_REQUEST = "HTTP_REQUEST";
  public static final String REQUEST_FAILED = "REQUEST_FAILED";
  public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
  public static final String ORDER_RECORDED = "ORDER_RECORDED";
  public static final String STOCK_ADJUSTED = "STOCK_ADJUSTED";
  public static final String INBOUND_RECORDED = "INBOUND_RECORDED";
  public static final String OUTBOX_PUBLISHED = "OUTBOX_PUBLISHED";
  public static final String OUTBOX_PUBLISH_FAILED = "OUTBOX_PUBLISH_FAILED";
  public static final String MESSAGE_DUPLICATE_SKIPPED = "MESSAGE_DUPLICATE_SKIPPED";
  public static final String MESSAGE_DEAD_LETTERED = "MESSAGE_DEAD_LETTERED";
  public static final String TRIGGER_EVALUATED = "TRIGGER_EVALUATED";
  public static final String SUGGESTION_DEDUPLICATED = "SUGGESTION_DEDUPLICATED";
  public static final String SUGGESTION_CREATED = "SUGGESTION_CREATED";
  public static final String SUGGESTION_ACCEPTED = "SUGGESTION_ACCEPTED";
  public static final String SUGGESTION_REJECTED = "SUGGESTION_REJECTED";
  public static final String SUGGESTION_SUPERSEDED = "SUGGESTION_SUPERSEDED";
  public static final String STRATEGY_CHANGED = "STRATEGY_CHANGED";
  public static final String LLM_CALL = "LLM_CALL";
  public static final String LLM_FALLBACK = "LLM_FALLBACK";

  private LogEvents() {}
}

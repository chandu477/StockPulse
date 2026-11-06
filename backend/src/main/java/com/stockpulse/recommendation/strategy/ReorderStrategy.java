package com.stockpulse.recommendation.strategy;

/** Reorder contract shared by HTTP and async callers (ADR-2). */
public interface ReorderStrategy {
  String key();

  ReorderAdvice advise(CommerceContext context);
}

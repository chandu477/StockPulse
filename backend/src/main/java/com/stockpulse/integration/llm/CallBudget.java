package com.stockpulse.integration.llm;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;

/** Sliding one-minute call budget (ADR-4): protects free-tier quotas. Over budget -> fallback. */
final class CallBudget {
  private final int maxPerMinute;
  private final Clock clock;
  private final Deque<Long> calls = new ArrayDeque<>();

  CallBudget(int maxPerMinute, Clock clock) {
    this.maxPerMinute = maxPerMinute;
    this.clock = clock;
  }

  synchronized boolean tryAcquire() {
    long now = clock.millis();
    while (!calls.isEmpty() && now - calls.peekFirst() >= 60_000) {
      calls.pollFirst();
    }
    if (calls.size() >= maxPerMinute) {
      return false;
    }
    calls.addLast(now);
    return true;
  }
}

package com.stockpulse.recommendation.ai;

import com.stockpulse.entity.FallbackReason;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.shared.logging.LogEvents;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared decorator logic: try AI, otherwise rule advice stamped with a fallbackReason. */
final class AiFallbackSupport {
  private static final Logger log = LoggerFactory.getLogger(AiFallbackSupport.class);

  private AiFallbackSupport() {}

  static <T> T adviseWithFallback(
      String type,
      CommerceContext ctx,
      AiAdvisor advisor,
      Supplier<T> ai,
      Function<FallbackReason, T> rule,
      MeterRegistry meters) {
    if (!advisor.isConfigured()) {
      return fallback(type, ctx, FallbackReason.NOT_CONFIGURED, rule, meters, "not configured");
    }
    try {
      return ai.get();
    } catch (AiAdviceException e) {
      return fallback(type, ctx, e.reason(), rule, meters, e.getMessage());
    } catch (RuntimeException e) {
      return fallback(
          type, ctx, FallbackReason.PROVIDER_ERROR, rule, meters, e.getClass().getSimpleName());
    }
  }

  private static <T> T fallback(
      String type,
      CommerceContext ctx,
      FallbackReason reason,
      Function<FallbackReason, T> rule,
      MeterRegistry meters,
      String detail) {
    meters.counter("stockpulse.llm.fallback", "reason", reason.name(), "type", type).increment();
    log.atWarn()
        .addKeyValue("event", LogEvents.LLM_FALLBACK)
        .addKeyValue("type", type)
        .addKeyValue("productId", ctx.product().id())
        .addKeyValue("trigger", ctx.trigger().name())
        .addKeyValue("fallbackReason", reason.name())
        .log("AI {} advice failed ({}): {}; using rule fallback", type, reason, detail);
    return rule.apply(reason);
  }
}

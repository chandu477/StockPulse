package com.stockpulse.service;

import com.stockpulse.entity.FallbackReason;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.PricingSuggestion;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.ReorderSuggestion;
import com.stockpulse.entity.Suggestion;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.guardrail.PriceGuardrail;
import com.stockpulse.recommendation.strategy.CommerceContext;
import com.stockpulse.recommendation.strategy.PricingAdvice;
import com.stockpulse.recommendation.strategy.PricingStrategy;
import com.stockpulse.recommendation.strategy.ReorderAdvice;
import com.stockpulse.recommendation.strategy.ReorderStrategy;
import com.stockpulse.recommendation.strategy.StrategyRegistry;
import com.stockpulse.recommendation.strategy.StrategyRegistry.Resolved;
import com.stockpulse.shared.logging.LogEvents;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single entry point that turns (product, trigger) into PENDING suggestions, used by both the
 * HTTP endpoints and the agentic loop ("two callers, one interface").
 *
 * <p>One recommendation per product (ADR-7 revision): at most one PENDING pricing and one PENDING
 * reorder suggestion per product. A repeat of the same trigger is de-duplicated (no extra LLM
 * call); a different trigger, or an explicit recalculation, generates fresh advice against current
 * stock and price and supersedes the older PENDING suggestion.
 *
 * <p>Three phases so the LLM is never called inside a transaction (BR-2): short read, strategies
 * with no transaction (pricing and reorder in parallel), short write under a product row lock.
 */
@Service
public class RecommendationService {
  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final Logger audit = LoggerFactory.getLogger("stockpulse.audit");
  private static final String RECALCULATED = "Superseded by a recalculated recommendation.";

  private final ProductService products;
  private final PricingSuggestionRepository pricingRepo;
  private final ReorderSuggestionRepository reorderRepo;
  private final StrategyRegistry registry;
  private final List<PriceGuardrail> guardrails;
  private final TransactionTemplate tx;
  private final Executor executor;
  private final Clock clock;

  public RecommendationService(
      ProductService products,
      PricingSuggestionRepository pricingRepo,
      ReorderSuggestionRepository reorderRepo,
      StrategyRegistry registry,
      List<PriceGuardrail> guardrails,
      TransactionTemplate tx,
      @Qualifier("strategyExecutor") Executor executor,
      Clock clock) {
    this.products = products;
    this.pricingRepo = pricingRepo;
    this.reorderRepo = reorderRepo;
    this.registry = registry;
    this.guardrails = guardrails;
    this.tx = tx;
    this.executor = executor;
    this.clock = clock;
  }

  /** Outcome per type: the PENDING suggestion (new or existing) and whether it was created now. */
  public record Result(
      PricingSuggestion pricing,
      boolean pricingCreated,
      ReorderSuggestion reorder,
      boolean reorderCreated) {}

  /** Agentic loop: same trigger again is de-duplicated; a different trigger recalculates. */
  public Result generate(String productId, TriggerReason trigger, Set<SuggestionType> types) {
    return run(productId, trigger, types, false);
  }

  /** Merchandiser asked for fresh numbers: always recalculates (trigger MANUAL). */
  public Result recalculate(String productId, Set<SuggestionType> types) {
    return run(productId, TriggerReason.MANUAL, types, true);
  }

  private Result run(
      String productId, TriggerReason trigger, Set<SuggestionType> types, boolean force) {
    String key = Suggestion.pendingKey(productId);

    // Phase 1: short reads.
    ProductView view = products.view(productId);
    PeerStats peers = products.peerStats(view);
    Optional<PricingSuggestion> existingPricing =
        types.contains(SuggestionType.PRICING)
            ? pricingRepo.findByPendingKey(key)
            : Optional.empty();
    Optional<ReorderSuggestion> existingReorder =
        types.contains(SuggestionType.REORDER)
            ? reorderRepo.findByPendingKey(key)
            : Optional.empty();
    boolean needPricing =
        types.contains(SuggestionType.PRICING) && needsNew(existingPricing, trigger, force);
    boolean needReorder =
        types.contains(SuggestionType.REORDER) && needsNew(existingReorder, trigger, force);
    if (!needPricing && !needReorder) {
      logDedup(productId, trigger);
      return new Result(existingPricing.orElse(null), false, existingReorder.orElse(null), false);
    }

    // Phase 2: no transaction. Strategies (possibly the LLM) run in parallel.
    CommerceContext ctx = new CommerceContext(view, trigger, peers);
    Resolved<PricingStrategy> ps = registry.activePricing();
    Resolved<ReorderStrategy> rs = registry.activeReorder();
    CompletableFuture<PricingAdvice> pricingF =
        needPricing ? async(() -> safePricing(ps, ctx)) : CompletableFuture.completedFuture(null);
    CompletableFuture<ReorderAdvice> reorderF =
        needReorder ? async(() -> safeReorder(rs, ctx)) : CompletableFuture.completedFuture(null);
    PricingAdvice pricingAdvice = applyGuardrails(pricingF.join(), ctx);
    ReorderAdvice reorderAdvice = reorderF.join();

    // Phase 3: short write, serialised per product by the row lock.
    return tx.execute(status -> {
      Product locked = products.lockForUpdate(productId);
      Instant now = Instant.now(clock);
      PricingSuggestion pricing = existingPricing.orElse(null);
      boolean pricingCreated = false;
      if (pricingAdvice != null) {
        Optional<PricingSuggestion> current = pricingRepo.findByPendingKey(key);
        if (current.isPresent() && !needsNew(current, trigger, force)) {
          pricing = current.get();
          logDedup(productId, trigger);
        } else {
          current.ifPresent(c -> supersede(c, pricingRepo, now));
          pricing = pricingRepo.save(toEntity(view, trigger, ps.key(), pricingAdvice, now));
          pricingCreated = true;
          logCreated(pricing, SuggestionType.PRICING);
        }
      }
      ReorderSuggestion reorder = existingReorder.orElse(null);
      boolean reorderCreated = false;
      if (reorderAdvice != null) {
        Optional<ReorderSuggestion> current = reorderRepo.findByPendingKey(key);
        if (current.isPresent() && !needsNew(current, trigger, force)) {
          reorder = current.get();
          logDedup(productId, trigger);
        } else {
          current.ifPresent(c -> supersede(c, reorderRepo, now));
          reorder = reorderRepo.save(toEntity(view, trigger, rs.key(), reorderAdvice, now));
          reorderCreated = true;
          logCreated(reorder, SuggestionType.REORDER);
        }
      }
      products.refreshStatus(locked);
      return new Result(pricing, pricingCreated, reorder, reorderCreated);
    });
  }

  private static boolean needsNew(
      Optional<? extends Suggestion> existing, TriggerReason trigger, boolean force) {
    return force || existing.isEmpty() || existing.get().getTriggerReason() != trigger;
  }

  /** Frees the product's pending key before the replacement is inserted (explicit flush). */
  private static <S extends Suggestion> void supersede(
      S old, JpaRepository<S, Long> repo, Instant now) {
    old.supersede(RECALCULATED, now);
    repo.saveAndFlush(old);
    audit
        .atInfo()
        .addKeyValue("event", LogEvents.SUGGESTION_SUPERSEDED)
        .addKeyValue("suggestionId", old.getId())
        .addKeyValue("productId", old.getProductId())
        .addKeyValue("reason", "RECALCULATED")
        .log("Suggestion {} superseded by a recalculation", old.getId());
  }

  /** Never lets a strategy bug drop a suggestion: falls back to the rule strategy (BR-3). */
  private PricingAdvice safePricing(Resolved<PricingStrategy> ps, CommerceContext ctx) {
    try {
      return ps.strategy().advise(ctx);
    } catch (RuntimeException e) {
      if (ps.strategy() == registry.rulePricing()) {
        throw e;
      }
      logFallback(ctx, "pricing", e);
      return registry.rulePricing().advise(ctx).asFallback(FallbackReason.PROVIDER_ERROR);
    }
  }

  private ReorderAdvice safeReorder(Resolved<ReorderStrategy> rs, CommerceContext ctx) {
    try {
      return rs.strategy().advise(ctx);
    } catch (RuntimeException e) {
      if (rs.strategy() == registry.ruleReorder()) {
        throw e;
      }
      logFallback(ctx, "reorder", e);
      return registry.ruleReorder().advise(ctx).asFallback(FallbackReason.PROVIDER_ERROR);
    }
  }

  private PricingAdvice applyGuardrails(PricingAdvice advice, CommerceContext ctx) {
    if (advice == null) {
      return null;
    }
    PricingAdvice result = advice;
    for (PriceGuardrail g : guardrails) {
      result = g.apply(result, ctx);
    }
    return result;
  }

  private <T> CompletableFuture<T> async(Supplier<T> work) {
    return CompletableFuture.supplyAsync(work, executor);
  }

  private static PricingSuggestion toEntity(
      ProductView v, TriggerReason trigger, String key, PricingAdvice a, Instant now) {
    String strategyKey = a.source() == SuggestionSource.RULE_FALLBACK ? key + "->rule" : key;
    return new PricingSuggestion(
        v.id(),
        v.currentPrice(),
        a.recommendedPrice(),
        a.direction(),
        a.confidence(),
        a.reasoning(),
        trigger,
        a.source(),
        strategyKey,
        a.fallbackReason(),
        now);
  }

  private static ReorderSuggestion toEntity(
      ProductView v, TriggerReason trigger, String key, ReorderAdvice a, Instant now) {
    String strategyKey = a.source() == SuggestionSource.RULE_FALLBACK ? key + "->rule" : key;
    return new ReorderSuggestion(
        v.id(),
        v.stockLevel(),
        a.recommendedQuantity(),
        a.leadTimeDays(),
        a.confidence(),
        a.reasoning(),
        trigger,
        a.source(),
        strategyKey,
        a.fallbackReason(),
        now);
  }

  private static void logDedup(String productId, TriggerReason trigger) {
    log.atInfo()
        .addKeyValue("event", LogEvents.SUGGESTION_DEDUPLICATED)
        .addKeyValue("productId", productId)
        .addKeyValue("trigger", trigger.name())
        .log("PENDING suggestion for {} already reflects {}", productId, trigger);
  }

  private static void logCreated(Suggestion s, SuggestionType type) {
    audit
        .atInfo()
        .addKeyValue("event", LogEvents.SUGGESTION_CREATED)
        .addKeyValue("suggestionId", s.getId())
        .addKeyValue("type", type.name())
        .addKeyValue("productId", s.getProductId())
        .addKeyValue("trigger", s.getTriggerReason().name())
        .addKeyValue("source", s.getSource().name())
        .addKeyValue(
            "fallbackReason", s.getFallbackReason() == null ? null : s.getFallbackReason().name())
        .log("{} suggestion {} created for {}", type, s.getId(), s.getProductId());
  }

  private static void logFallback(CommerceContext ctx, String type, RuntimeException e) {
    log.atWarn()
        .addKeyValue("event", LogEvents.LLM_FALLBACK)
        .addKeyValue("productId", ctx.product().id())
        .addKeyValue("type", type)
        .addKeyValue("fallbackReason", FallbackReason.PROVIDER_ERROR.name())
        .log(
            "Strategy failed unexpectedly ({}); using rule fallback", e.getClass().getSimpleName());
  }
}

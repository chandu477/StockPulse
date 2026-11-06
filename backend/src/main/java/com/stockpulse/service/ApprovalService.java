package com.stockpulse.service;

import com.stockpulse.entity.PricingSuggestion;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ReorderSuggestion;
import com.stockpulse.entity.Suggestion;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.logging.LogEvents;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The human checkpoint (FR-8, AGT-4). Each decision is one transaction: validate PENDING -> side
 * effect (price or stock) -> mark decided -> supersede same-type siblings (ADR-7) -> re-derive
 * status. Accepting one type never touches the other type's suggestions.
 */
@Service
public class ApprovalService {
  private static final Logger audit = LoggerFactory.getLogger("stockpulse.audit");

  private final PricingSuggestionRepository pricingRepo;
  private final ReorderSuggestionRepository reorderRepo;
  private final ProductService products;
  private final InventoryService inventory;
  private final Clock clock;

  public ApprovalService(
      PricingSuggestionRepository pricingRepo,
      ReorderSuggestionRepository reorderRepo,
      ProductService products,
      InventoryService inventory,
      Clock clock) {
    this.pricingRepo = pricingRepo;
    this.reorderRepo = reorderRepo;
    this.products = products;
    this.inventory = inventory;
    this.clock = clock;
  }

  public enum Decision {
    ACCEPTED,
    REJECTED
  }

  public record Decided<T>(T suggestion, Product product) {}

  @Transactional
  public Decided<PricingSuggestion> decidePricing(long id, Decision decision, String note) {
    PricingSuggestion s =
        pricingRepo.findById(id).orElseThrow(() -> ApiException.suggestionNotFound(id));
    Product product = products.lockForUpdate(s.getProductId());
    Instant now = Instant.now(clock);
    if (decision == Decision.ACCEPTED) {
      s.accept(note, now);
      products.applyAcceptedPrice(product, s.getRecommendedPrice());
      supersede(
          pricingRepo.findByProductIdAndStatus(product.getId(), SuggestionStatus.PENDING), now);
    } else {
      s.reject(note, now);
    }
    pricingRepo.flush();
    products.refreshStatus(product);
    logDecision(s, decision, "PRICING");
    return new Decided<>(s, product);
  }

  @Transactional
  public Decided<ReorderSuggestion> decideReorder(long id, Decision decision, String note) {
    ReorderSuggestion s =
        reorderRepo.findById(id).orElseThrow(() -> ApiException.suggestionNotFound(id));
    Product product = products.lockForUpdate(s.getProductId());
    Instant now = Instant.now(clock);
    if (decision == Decision.ACCEPTED) {
      s.accept(note, now);
      supersede(
          reorderRepo.findByProductIdAndStatus(product.getId(), SuggestionStatus.PENDING), now);
      reorderRepo.flush();
      inventory.recordInbound(product, s.getRecommendedQuantity());
    } else {
      s.reject(note, now);
    }
    reorderRepo.flush();
    products.refreshStatus(product);
    logDecision(s, decision, "REORDER");
    return new Decided<>(s, product);
  }

  private static void supersede(List<? extends Suggestion> siblings, Instant now) {
    for (Suggestion sibling : siblings) {
      sibling.supersede(now);
      audit
          .atInfo()
          .addKeyValue("event", LogEvents.SUGGESTION_SUPERSEDED)
          .addKeyValue("suggestionId", sibling.getId())
          .addKeyValue("productId", sibling.getProductId())
          .log("Suggestion {} superseded", sibling.getId());
    }
  }

  private static void logDecision(Suggestion s, Decision decision, String type) {
    audit
        .atInfo()
        .addKeyValue(
            "event",
            decision == Decision.ACCEPTED
                ? LogEvents.SUGGESTION_ACCEPTED
                : LogEvents.SUGGESTION_REJECTED)
        .addKeyValue("suggestionId", s.getId())
        .addKeyValue("type", type)
        .addKeyValue("productId", s.getProductId())
        .log("{} suggestion {} {}", type, s.getId(), decision);
  }
}

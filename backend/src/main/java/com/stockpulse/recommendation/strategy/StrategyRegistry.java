package com.stockpulse.recommendation.strategy;

import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.service.RuntimeSettings;
import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.error.ErrorCode;
import com.stockpulse.shared.error.FieldErrorItem;
import com.stockpulse.shared.logging.LogEvents;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runtime strategy switching (ADR-3). Every registered strategy bean is found by its key(); the
 * active key is read from persisted RuntimeSettings on every call, so HTTP and async callers see a
 * switch on their next call with no restart.
 */
@Component
public class StrategyRegistry {
  static final String PRICING_SETTING = "strategy.pricing";
  static final String REORDER_SETTING = "strategy.reorder";
  private static final Logger audit = LoggerFactory.getLogger("stockpulse.audit");

  private final Map<String, PricingStrategy> pricing;
  private final Map<String, ReorderStrategy> reorder;
  private final RuntimeSettings settings;
  private final RecommendationProperties.Strategy defaults;

  public StrategyRegistry(
      List<PricingStrategy> pricing,
      List<ReorderStrategy> reorder,
      RuntimeSettings settings,
      RecommendationProperties props) {
    this.pricing = index(pricing, PricingStrategy::key);
    this.reorder = index(reorder, ReorderStrategy::key);
    this.settings = settings;
    this.defaults = props.strategy();
  }

  public record Resolved<T>(String key, T strategy) {}

  public Resolved<PricingStrategy> activePricing() {
    String key = settings.get(PRICING_SETTING, defaults.defaultPricing());
    PricingStrategy s = pricing.getOrDefault(key, pricing.get("rule"));
    return new Resolved<>(pricing.containsKey(key) ? key : "rule", s);
  }

  public Resolved<ReorderStrategy> activeReorder() {
    String key = settings.get(REORDER_SETTING, defaults.defaultReorder());
    ReorderStrategy s = reorder.getOrDefault(key, reorder.get("rule"));
    return new Resolved<>(reorder.containsKey(key) ? key : "rule", s);
  }

  public PricingStrategy rulePricing() {
    return pricing.get("rule");
  }

  public ReorderStrategy ruleReorder() {
    return reorder.get("rule");
  }

  public List<String> pricingKeys() {
    return List.copyOf(pricing.keySet());
  }

  public List<String> reorderKeys() {
    return List.copyOf(reorder.keySet());
  }

  public void switchTo(String pricingKey, String reorderKey) {
    validate("pricing", pricingKey, pricing.keySet());
    validate("reorder", reorderKey, reorder.keySet());
    if (pricingKey != null) {
      settings.put(PRICING_SETTING, pricingKey);
    }
    if (reorderKey != null) {
      settings.put(REORDER_SETTING, reorderKey);
    }
    audit
        .atInfo()
        .addKeyValue("event", LogEvents.STRATEGY_CHANGED)
        .addKeyValue("pricing", activePricing().key())
        .addKeyValue("reorder", activeReorder().key())
        .log("Strategies switched");
  }

  private static void validate(String field, String key, java.util.Set<String> valid) {
    if (key != null && !valid.contains(key)) {
      throw new ApiException(
          ErrorCode.UNKNOWN_STRATEGY,
          "Unknown %s strategy '%s'. Valid: %s".formatted(field, key, valid),
          List.of(new FieldErrorItem(field, "must be one of " + valid, key)));
    }
  }

  private static <T> Map<String, T> index(List<T> beans, Function<T, String> key) {
    return beans.stream()
        .collect(Collectors.toMap(key, Function.identity(), (a, b) -> a, TreeMap::new));
  }
}

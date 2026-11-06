package com.stockpulse.recommendation.ai;

import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.TriggerReason;
import com.stockpulse.recommendation.config.RecommendationProperties;
import com.stockpulse.recommendation.strategy.CommerceContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Six templates: {pricing,reorder} x {inventory-low, demand-spike, manual} (plan §C.7). The trigger
 * chooses a genuinely different prompt, not the same text with one field changed.
 */
@Component
public class PromptLibrary {
  private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");
  private final Map<String, String> templates = new HashMap<>();
  private final RecommendationProperties props;

  public PromptLibrary(RecommendationProperties props) {
    this.props = props;
    for (String type : new String[] {"pricing", "reorder"}) {
      for (String trigger : new String[] {"inventory-low", "demand-spike", "manual"}) {
        load(type + "-" + trigger);
      }
    }
    load("system");
  }

  public String system() {
    return templates.get("system");
  }

  public String pricing(CommerceContext ctx) {
    return render("pricing-" + slug(ctx.trigger()), variables(ctx));
  }

  public String reorder(CommerceContext ctx) {
    return render("reorder-" + slug(ctx.trigger()), variables(ctx));
  }

  static String slug(TriggerReason trigger) {
    return switch (trigger) {
      case INVENTORY_LOW -> "inventory-low";
      case DEMAND_SPIKE -> "demand-spike";
      case MANUAL, INITIAL -> "manual";
    };
  }

  Map<String, String> variables(CommerceContext ctx) {
    ProductView p = ctx.product();
    BigDecimal price = p.currentPrice();
    BigDecimal peerAvg = ctx.peers().averageVelocity();
    int leadTime = props.reorder().defaultLeadTimeDays();
    int coverTarget = p.reorderThreshold() * props.reorder().coverMultiplier();
    Map<String, String> v = new HashMap<>();
    v.put("name", sanitize(p.name()));
    v.put("sku", sanitize(p.sku()));
    v.put("category", p.category().name());
    v.put("price", money(price));
    v.put("costPrice", p.costPrice() == null ? "unknown" : money(p.costPrice()));
    v.put("stock", String.valueOf(p.stockLevel()));
    v.put("threshold", String.valueOf(p.reorderThreshold()));
    v.put(
        "stockVsThreshold",
        p.reorderThreshold() == 0 ? "n/a" : (p.stockLevel() * 100 / p.reorderThreshold()) + "%");
    v.put("velocity", String.valueOf(p.demandVelocity()));
    v.put("peerAvg", peerAvg.toPlainString());
    v.put("peerCount", String.valueOf(ctx.peers().peerCount()));
    v.put(
        "velocityMultiple",
        peerAvg.signum() == 0
            ? "n/a"
            : BigDecimal.valueOf(p.demandVelocity())
                .divide(peerAvg, 1, RoundingMode.HALF_UP)
                .toPlainString());
    v.put(
        "daysOfCover",
        p.demandVelocity() == 0
            ? "no recent sales"
            : BigDecimal.valueOf(p.stockLevel())
                .divide(BigDecimal.valueOf(p.demandVelocity()), 1, RoundingMode.HALF_UP)
                .toPlainString());
    v.put("leadTime", String.valueOf(leadTime));
    v.put("leadTimeDemand", String.valueOf(p.demandVelocity() * leadTime));
    v.put("coverTarget", String.valueOf(coverTarget));
    v.put("coverMultiplier", String.valueOf(props.reorder().coverMultiplier()));
    v.put("ruleQuantity", String.valueOf(Math.max(1, coverTarget - p.stockLevel())));
    v.put("maxQuantity", String.valueOf(props.reorder().maxQuantity()));
    v.put("spikeMultiplier", props.trigger().spikeMultiplier().toPlainString());
    v.put("minPrice", money(price.multiply(props.pricing().minRatio())));
    v.put("maxPrice", money(price.multiply(props.pricing().maxRatio())));
    return v;
  }

  private String render(String name, Map<String, String> vars) {
    Matcher m = VAR.matcher(templates.get(name));
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String value = vars.get(m.group(1));
      if (value == null) {
        throw new IllegalStateException("Prompt " + name + " uses unknown variable " + m.group(1));
      }
      m.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    m.appendTail(out);
    return out.toString();
  }

  /** Product text is data: strip the delimiters so it cannot close the data block. */
  static String sanitize(String text) {
    return text.replace("<<<", "").replace(">>>", "").replace("DATA", "data").strip();
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private void load(String name) {
    try (var in = new ClassPathResource("prompts/" + name + ".txt").getInputStream()) {
      templates.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Missing prompt template " + name, e);
    }
  }
}

package com.stockpulse.controller;

import com.stockpulse.recommendation.strategy.StrategyRegistry;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime strategy switch (ADR-3). Unprotected in the MVP; ADMIN role in production (Q-13). */
@RestController
@RequestMapping("/api/v1/admin/strategies")
public class StrategyAdminController {
  private final StrategyRegistry registry;

  public StrategyAdminController(StrategyRegistry registry) {
    this.registry = registry;
  }

  public record Available(List<String> pricing, List<String> reorder) {}

  public record StrategySettings(String pricing, String reorder, Available available) {}

  public record StrategyUpdateRequest(
      @Size(max = 40) String pricing, @Size(max = 40) String reorder) {}

  @GetMapping
  StrategySettings get() {
    return current();
  }

  @PutMapping
  StrategySettings update(@jakarta.validation.Valid @RequestBody StrategyUpdateRequest req) {
    registry.switchTo(req.pricing(), req.reorder());
    return current();
  }

  private StrategySettings current() {
    return new StrategySettings(
        registry.activePricing().key(),
        registry.activeReorder().key(),
        new Available(registry.pricingKeys(), registry.reorderKeys()));
  }
}

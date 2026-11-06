package com.stockpulse.controller;

import com.stockpulse.entity.ProductView;
import com.stockpulse.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DecisionController {
  private final ApprovalService approvals;

  public DecisionController(ApprovalService approvals) {
    this.approvals = approvals;
  }

  @PatchMapping("/pricing-suggestions/{suggestionId}")
  PricingSuggestionResponse decidePricing(
      @PathVariable long suggestionId, @Valid @RequestBody DecisionRequest req) {
    var d = approvals.decidePricing(suggestionId, req.status(), req.note());
    return PricingSuggestionResponse.from(d.suggestion(), ProductView.of(d.product()));
  }

  @PatchMapping("/reorder-suggestions/{suggestionId}")
  ReorderSuggestionResponse decideReorder(
      @PathVariable long suggestionId, @Valid @RequestBody DecisionRequest req) {
    var d = approvals.decideReorder(suggestionId, req.status(), req.note());
    return ReorderSuggestionResponse.from(d.suggestion(), ProductView.of(d.product()));
  }
}

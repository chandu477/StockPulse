package com.stockpulse.controller;

import com.stockpulse.entity.PricingSuggestion;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.ReorderSuggestion;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.service.PricingSuggestionRepository;
import com.stockpulse.service.ProductService;
import com.stockpulse.service.RecommendationService;
import com.stockpulse.service.ReorderSuggestionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** On-demand recalculation (MANUAL, supersedes the pending one), review queue, price history. */
@RestController
@RequestMapping("/api/v1")
public class SuggestionController {
  private final RecommendationService recommendations;
  private final PricingSuggestionRepository pricingRepo;
  private final ReorderSuggestionRepository reorderRepo;
  private final ProductService products;

  public SuggestionController(
      RecommendationService recommendations,
      PricingSuggestionRepository pricingRepo,
      ReorderSuggestionRepository reorderRepo,
      ProductService products) {
    this.recommendations = recommendations;
    this.pricingRepo = pricingRepo;
    this.reorderRepo = reorderRepo;
    this.products = products;
  }

  @PostMapping("/products/{productId}/suggest-pricing")
  ResponseEntity<PricingSuggestionResponse> suggestPricing(@PathVariable String productId) {
    var result = recommendations.recalculate(productId, Set.of(SuggestionType.PRICING));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(PricingSuggestionResponse.from(result.pricing(), products.view(productId)));
  }

  @PostMapping("/products/{productId}/suggest-reorder")
  ResponseEntity<ReorderSuggestionResponse> suggestReorder(@PathVariable String productId) {
    var result = recommendations.recalculate(productId, Set.of(SuggestionType.REORDER));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ReorderSuggestionResponse.from(result.reorder(), products.view(productId)));
  }

  @GetMapping("/pricing-suggestions")
  @Transactional(readOnly = true)
  public List<PricingSuggestionResponse> listPricing(
      @RequestParam(required = false) SuggestionStatus status,
      @RequestParam(required = false) String productId) {
    List<PricingSuggestion> found = pricingRepo.search(status, productId);
    Map<String, ProductView> views =
        products.views(found.stream().map(PricingSuggestion::getProductId).distinct().toList());
    return found.stream()
        .map(s -> PricingSuggestionResponse.from(s, views.get(s.getProductId())))
        .toList();
  }

  @GetMapping("/reorder-suggestions")
  @Transactional(readOnly = true)
  public List<ReorderSuggestionResponse> listReorder(
      @RequestParam(required = false) SuggestionStatus status,
      @RequestParam(required = false) String productId) {
    List<ReorderSuggestion> found = reorderRepo.search(status, productId);
    Map<String, ProductView> views =
        products.views(found.stream().map(ReorderSuggestion::getProductId).distinct().toList());
    return found.stream()
        .map(s -> ReorderSuggestionResponse.from(s, views.get(s.getProductId())))
        .toList();
  }

  @GetMapping("/products/{productId}/price-history")
  @Transactional(readOnly = true)
  public List<PricePointResponse> priceHistory(@PathVariable String productId) {
    products.view(productId); // 404 if unknown
    return pricingRepo
        .findByProductIdAndStatusOrderByDecidedAtAsc(productId, SuggestionStatus.ACCEPTED)
        .stream()
        .map(s -> new PricePointResponse(s.getDecidedAt(), s.getRecommendedPrice(), s.getId()))
        .toList();
  }
}

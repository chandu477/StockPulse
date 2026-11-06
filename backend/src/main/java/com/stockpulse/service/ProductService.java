package com.stockpulse.service;

import com.stockpulse.entity.Category;
import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.PendingSuggestionQuery;
import com.stockpulse.entity.PendingSuggestionQuery.PendingCounts;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.entity.ProductStatusPolicy;
import com.stockpulse.entity.ProductView;
import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.error.ErrorCode;
import com.stockpulse.shared.logging.LogEvents;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
  private static final Logger log = LoggerFactory.getLogger(ProductService.class);
  private final ProductRepository products;
  private final PendingSuggestionQuery pending;
  private final Clock clock;

  public ProductService(ProductRepository products, PendingSuggestionQuery pending, Clock clock) {
    this.products = products;
    this.pending = pending;
    this.clock = clock;
  }

  public record NewProduct(
      String sku,
      String name,
      Category category,
      BigDecimal currentPrice,
      int stockLevel,
      int reorderThreshold,
      int demandVelocity,
      BigDecimal costPrice,
      String supplierId) {}

  /** Product plus the pending counts the API shows. */
  public record ProductDetails(Product product, PendingCounts pending) {}

  @Transactional
  public ProductDetails create(NewProduct cmd) {
    if (products.existsBySku(cmd.sku())) {
      throw new ApiException(ErrorCode.DUPLICATE_SKU, "SKU " + cmd.sku() + " already exists.");
    }
    String id = "PRD-%03d".formatted(products.nextIdValue());
    Product p =
        new Product(
            id,
            cmd.sku(),
            cmd.name(),
            cmd.category(),
            cmd.currentPrice(),
            cmd.stockLevel(),
            cmd.reorderThreshold(),
            cmd.demandVelocity(),
            cmd.costPrice(),
            cmd.supplierId(),
            Instant.now(clock));
    products.saveAndFlush(p);
    log.atInfo()
        .addKeyValue("event", LogEvents.PRODUCT_CREATED)
        .addKeyValue("productId", id)
        .log("Product {} created", id);
    return new ProductDetails(p, PendingCounts.NONE);
  }

  @Transactional(readOnly = true)
  public List<ProductDetails> list(ProductStatus status, Category category) {
    List<Product> found = products.search(status, category);
    Map<String, PendingCounts> counts =
        pending.countsFor(found.stream().map(Product::getId).toList());
    return found.stream()
        .map(p -> new ProductDetails(p, counts.getOrDefault(p.getId(), PendingCounts.NONE)))
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductDetails get(String id) {
    Product p = products.findById(id).orElseThrow(() -> ApiException.productNotFound(id));
    return new ProductDetails(p, pending.countsFor(id));
  }

  @Transactional(readOnly = true)
  public ProductView view(String id) {
    return products
        .findById(id)
        .map(ProductView::of)
        .orElseThrow(() -> ApiException.productNotFound(id));
  }

  @Transactional(readOnly = true)
  public Map<String, ProductView> views(java.util.Collection<String> ids) {
    return products.findAllById(ids).stream()
        .collect(java.util.stream.Collectors.toMap(Product::getId, ProductView::of));
  }

  /** Locks the product row for the current transaction (serialises writers per product). */
  @Transactional(propagation = Propagation.MANDATORY)
  public Product lockForUpdate(String id) {
    return products.findByIdForUpdate(id).orElseThrow(() -> ApiException.productNotFound(id));
  }

  @Transactional(readOnly = true)
  public PeerStats peerStats(ProductView product) {
    Object[] row = products.peerVelocity(product.category(), product.id()).getFirst();
    long count = ((Number) row[1]).longValue();
    if (count == 0) {
      return PeerStats.NONE;
    }
    BigDecimal avg = new BigDecimal(row[0].toString()).setScale(2, RoundingMode.HALF_UP);
    return new PeerStats(avg, count);
  }

  /** Re-derives status after any write (ADR-7). Must run inside the writer's transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refreshStatus(Product product) {
    boolean hasPending = pending.countsFor(product.getId()).any();
    product.applyDerivedStatus(
        ProductStatusPolicy.derive(product.getStockLevel(), hasPending), Instant.now(clock));
  }

  /** BR-1: the only way a price changes. Called by the approval module on accept. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void applyAcceptedPrice(Product product, BigDecimal newPrice) {
    product.applyAcceptedPrice(newPrice, Instant.now(clock));
  }
}

package com.stockpulse.service;

import com.stockpulse.entity.InventoryEvent;
import com.stockpulse.entity.InventoryEventType;
import com.stockpulse.entity.Product;
import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.error.ErrorCode;
import com.stockpulse.shared.logging.LogEvents;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock changes. Each method is one transaction: product row + inventory event + outbox row +
 * derived status. No AI or broker work happens here (agentic loop is after commit, ADR-5).
 */
@Service
public class InventoryService {
  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
  private final ProductService products;
  private final InventoryEventRepository events;
  private final OutboxWriter outbox;
  private final Clock clock;

  public InventoryService(
      ProductService products, InventoryEventRepository events, OutboxWriter outbox, Clock clock) {
    this.products = products;
    this.events = events;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Product recordOrder(String productId, int quantity) {
    Product p = products.lockForUpdate(productId);
    if (quantity > p.getStockLevel()) {
      throw new ApiException(
          ErrorCode.INSUFFICIENT_STOCK,
          "Only %d units of %s are in stock.".formatted(p.getStockLevel(), productId));
    }
    Instant now = Instant.now(clock);
    p.recordSale(quantity, now);
    record(p, InventoryEventType.ORDER, -quantity, now);
    log.atInfo()
        .addKeyValue("event", LogEvents.ORDER_RECORDED)
        .addKeyValue("productId", productId)
        .addKeyValue("quantity", quantity)
        .addKeyValue("stockAfter", p.getStockLevel())
        .log("Order of {} recorded for {}", quantity, productId);
    return p;
  }

  @Transactional
  public Product setStock(String productId, int stockLevel) {
    Product p = products.lockForUpdate(productId);
    Instant now = Instant.now(clock);
    int delta = stockLevel - p.getStockLevel();
    p.adjustStock(stockLevel, now);
    record(p, InventoryEventType.ADJUST, delta, now);
    log.atInfo()
        .addKeyValue("event", LogEvents.STOCK_ADJUSTED)
        .addKeyValue("productId", productId)
        .addKeyValue("stockAfter", stockLevel)
        .log("Stock of {} set to {}", productId, stockLevel);
    return p;
  }

  /** Simulated inbound shipment when a reorder suggestion is accepted (Q-5). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordInbound(Product lockedProduct, int quantity) {
    Instant now = Instant.now(clock);
    lockedProduct.receiveInbound(quantity, now);
    record(lockedProduct, InventoryEventType.INBOUND, quantity, now);
    log.atInfo()
        .addKeyValue("event", LogEvents.INBOUND_RECORDED)
        .addKeyValue("productId", lockedProduct.getId())
        .addKeyValue("quantity", quantity)
        .log("Inbound of {} recorded for {}", quantity, lockedProduct.getId());
  }

  private void record(Product p, InventoryEventType type, int delta, Instant now) {
    events.save(
        new InventoryEvent(p.getId(), type, delta, p.getStockLevel(), p.getDemandVelocity(), now));
    outbox.inventoryChanged(p, type.name());
    products.refreshStatus(p);
  }
}

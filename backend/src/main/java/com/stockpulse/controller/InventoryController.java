package com.stockpulse.controller;

import com.stockpulse.service.InventoryService;
import com.stockpulse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stock and simulated orders. Both return immediately; suggestions are generated async. */
@RestController
@RequestMapping("/api/v1/products/{productId}")
public class InventoryController {
  private final InventoryService inventory;
  private final ProductService products;

  public InventoryController(InventoryService inventory, ProductService products) {
    this.inventory = inventory;
    this.products = products;
  }

  @PatchMapping("/stock")
  ProductResponse updateStock(
      @PathVariable String productId, @Valid @RequestBody StockUpdateRequest req) {
    inventory.setStock(productId, req.stockLevel());
    return ProductResponse.from(products.get(productId));
  }

  @PostMapping("/orders")
  ResponseEntity<ProductResponse> order(
      @PathVariable String productId, @Valid @RequestBody OrderRequest req) {
    inventory.recordOrder(productId, req.quantity());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ProductResponse.from(products.get(productId)));
  }
}

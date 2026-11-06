package com.stockpulse.controller;

import com.stockpulse.entity.Category;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest req) {
    var created =
        service.create(
            new ProductService.NewProduct(
                req.sku().trim(),
                req.name().trim(),
                req.category(),
                req.currentPrice(),
                req.stockLevel(),
                req.reorderThreshold(),
                req.demandVelocity() == null ? 0 : req.demandVelocity(),
                req.costPrice(),
                req.supplierId()));
    ProductResponse body = ProductResponse.from(created);
    return ResponseEntity.created(URI.create("/api/v1/products/" + body.id())).body(body);
  }

  @GetMapping
  List<ProductResponse> list(
      @RequestParam(required = false) ProductStatus status,
      @RequestParam(required = false) Category category) {
    return service.list(status, category).stream().map(ProductResponse::from).toList();
  }

  @GetMapping("/{productId}")
  ProductResponse get(@PathVariable String productId) {
    return ProductResponse.from(service.get(productId));
  }
}

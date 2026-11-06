package com.stockpulse.controller;

import com.stockpulse.entity.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank @Size(max = 64) String sku,
    @NotBlank @Size(max = 200) String name,
    @NotNull Category category,
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal currentPrice,
    @NotNull @Min(0) Integer stockLevel,
    @NotNull @Min(0) Integer reorderThreshold,
    @Min(0) Integer demandVelocity,
    @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
    @Size(max = 64) String supplierId) {}

package com.stockpulse.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(@NotNull @Min(1) @Max(10000) Integer quantity) {}

package com.stockpulse.controller;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(Instant at, BigDecimal price, long suggestionId) {}

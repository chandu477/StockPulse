package com.stockpulse.shared.error;

public record FieldErrorItem(String field, String message, Object rejectedValue) {}

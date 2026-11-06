package com.stockpulse.service;

/** A stock/velocity change delivered by the messaging layer (outbox row id = messageId). */
public record InventorySignal(String messageId, String productId, String cause, String traceId) {}

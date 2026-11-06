package com.stockpulse.service;

/** Body of an INVENTORY_CHANGED outbox event / broker message. */
public record InventoryChangedPayload(
    String productId, String cause, int stockLevel, int demandVelocity) {}

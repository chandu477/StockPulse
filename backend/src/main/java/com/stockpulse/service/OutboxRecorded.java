package com.stockpulse.service;

import java.util.UUID;

/** Spring application event: an outbox row was written. Dispatchers act on it AFTER_COMMIT. */
public record OutboxRecorded(UUID outboxId) {}

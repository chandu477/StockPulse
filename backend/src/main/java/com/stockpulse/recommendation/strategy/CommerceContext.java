package com.stockpulse.recommendation.strategy;

import com.stockpulse.entity.PeerStats;
import com.stockpulse.entity.ProductView;
import com.stockpulse.entity.TriggerReason;

/** Everything a strategy may use. Built once per generation, outside any transaction. */
public record CommerceContext(ProductView product, TriggerReason trigger, PeerStats peers) {}

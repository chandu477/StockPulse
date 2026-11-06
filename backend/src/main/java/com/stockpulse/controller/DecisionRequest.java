package com.stockpulse.controller;

import com.stockpulse.service.ApprovalService.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@NotNull Decision status, @Size(max = 500) String note) {}

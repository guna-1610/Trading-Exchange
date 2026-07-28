package com.exchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
        @NotBlank String asset,
        @Positive long amount) {}

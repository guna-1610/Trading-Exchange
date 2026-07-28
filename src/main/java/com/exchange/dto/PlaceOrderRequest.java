package com.exchange.dto;

import com.exchange.domain.OrderType;
import com.exchange.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The account is taken from the authenticated bearer token, not the request
 * body, so a user can only ever trade on their own account. price is required
 * (&gt;0) for LIMIT orders and ignored for MARKET orders.
 */
public record PlaceOrderRequest(
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull OrderType type,
        @PositiveOrZero long price,
        @Positive long quantity) {}

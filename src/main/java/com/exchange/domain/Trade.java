package com.exchange.domain;

import java.time.Instant;

/**
 * An executed trade between a resting {@code maker} order and an incoming
 * {@code taker} order. The trade always prints at the maker's price — the maker
 * set the price by resting first (price-time priority).
 */
public record Trade(
        long id,
        String symbol,
        long price,
        long quantity,
        String makerOrderId,
        String takerOrderId,
        String buyAccountId,
        String sellAccountId,
        Side takerSide,
        Instant timestamp
) {
    public long notional() { return price * quantity; }
}

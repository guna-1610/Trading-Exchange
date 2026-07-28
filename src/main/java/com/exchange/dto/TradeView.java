package com.exchange.dto;

import com.exchange.domain.Trade;

public record TradeView(long id, String symbol, long price, long quantity, long notional,
                        String takerSide, String timestamp) {
    public static TradeView of(Trade t) {
        return new TradeView(t.id(), t.symbol(), t.price(), t.quantity(), t.notional(),
                t.takerSide().name(), t.timestamp().toString());
    }
}

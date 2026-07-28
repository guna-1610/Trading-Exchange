package com.exchange.dto;

import com.exchange.domain.Order;

public record OrderView(String id, String accountId, String symbol, String side,
                        String type, long price, long originalQty, long remainingQty,
                        long filledQty, String status, String createdAt) {
    public static OrderView of(Order o) {
        return new OrderView(o.id(), o.accountId(), o.symbol(), o.side().name(),
                o.type().name(), o.price(), o.originalQty(), o.remainingQty(),
                o.filledQty(), o.status().name(), o.createdAt().toString());
    }
}

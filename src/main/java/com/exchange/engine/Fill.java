package com.exchange.engine;

import com.exchange.domain.Order;

/** One execution between a resting maker order and an incoming taker order. */
public record Fill(Order maker, Order taker, long price, long quantity) {
    public long notional() { return price * quantity; }
}

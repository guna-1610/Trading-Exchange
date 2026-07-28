package com.exchange.domain;

import java.time.Instant;

/**
 * A resting or in-flight order. Mutable: {@code remainingQty} and {@code status}
 * change as the order fills. The {@code sequence} is a global monotonic counter
 * that establishes time priority between orders at the same price.
 */
public class Order {

    private final String id;
    private final long sequence;
    private final String accountId;
    private final String symbol;
    private final Side side;
    private final OrderType type;
    private final long price;          // 0 for MARKET orders
    private final long originalQty;
    private long remainingQty;
    private OrderStatus status;
    private final Instant createdAt;

    public Order(String id, long sequence, String accountId, String symbol, Side side,
                 OrderType type, long price, long originalQty) {
        this.id = id;
        this.sequence = sequence;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQty = originalQty;
        this.remainingQty = originalQty;
        this.status = OrderStatus.NEW;
        this.createdAt = Instant.now();
    }

    public long filledQty() { return originalQty - remainingQty; }

    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    /** Reduce remaining quantity by a fill and advance status. */
    public void fill(long qty) {
        if (qty <= 0 || qty > remainingQty) {
            throw new IllegalArgumentException("invalid fill qty " + qty + " for remaining " + remainingQty);
        }
        remainingQty -= qty;
        status = (remainingQty == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel()  { this.status = OrderStatus.CANCELLED; }
    public void reject()  { this.status = OrderStatus.REJECTED; }

    public String id() { return id; }
    public long sequence() { return sequence; }
    public String accountId() { return accountId; }
    public String symbol() { return symbol; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public long price() { return price; }
    public long originalQty() { return originalQty; }
    public long remainingQty() { return remainingQty; }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}

package com.exchange.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single instrument's central limit order book.
 *
 * <p>Each side is a price-sorted map of FIFO queues, giving <b>price-time
 * priority</b>: better prices match first, and within a price level the order
 * that arrived first (lowest sequence) matches first. Bids are sorted
 * descending (best = highest bid); asks ascending (best = lowest ask). Access to
 * the best level is O(log L) in the number of price levels.
 *
 * <p>This class is not thread-safe on its own; the {@code ExchangeService}
 * serialises all access to a given book under a per-symbol lock.
 */
public class OrderBook {

    private final Instrument instrument;
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();

    public OrderBook(Instrument instrument) { this.instrument = instrument; }

    public Instrument instrument() { return instrument; }

    private TreeMap<Long, Deque<Order>> book(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    /** The book on the opposite side to a taker (what it matches against). */
    public TreeMap<Long, Deque<Order>> opposite(Side takerSide) {
        return takerSide == Side.BUY ? asks : bids;
    }

    public Long bestBid() { return bids.isEmpty() ? null : bids.firstKey(); }
    public Long bestAsk() { return asks.isEmpty() ? null : asks.firstKey(); }

    /** Rest an order at its price (tail of its FIFO level). */
    public void rest(Order order) {
        book(order.side()).computeIfAbsent(order.price(), p -> new ArrayDeque<>()).addLast(order);
    }

    /** Remove a (possibly resting) order from the book by identity. */
    public boolean remove(Order order) {
        Map<Long, Deque<Order>> b = book(order.side());
        Deque<Order> level = b.get(order.price());
        if (level == null) return false;
        boolean removed = level.removeIf(o -> o.id().equals(order.id()));
        if (level.isEmpty()) b.remove(order.price());
        return removed;
    }

    /** Peek the best resting order on a side, or null. */
    public Order peekBest(Side side) {
        Map.Entry<Long, Deque<Order>> e = book(side).firstEntry();
        return e == null ? null : e.getValue().peekFirst();
    }

    /** Drop the current best order (called once it is fully filled). */
    public void pollBest(Side side) {
        TreeMap<Long, Deque<Order>> b = book(side);
        Map.Entry<Long, Deque<Order>> e = b.firstEntry();
        if (e == null) return;
        Deque<Order> level = e.getValue();
        level.pollFirst();
        if (level.isEmpty()) b.remove(e.getKey());
    }

    /** Aggregated L2 depth: total remaining quantity per price level, best first. */
    public List<long[]> depth(Side side, int levels) {
        List<long[]> out = new ArrayList<>();
        for (Map.Entry<Long, Deque<Order>> e : book(side).entrySet()) {
            long qty = 0;
            for (Order o : e.getValue()) qty += o.remainingQty();
            out.add(new long[]{e.getKey(), qty});
            if (out.size() >= levels) break;
        }
        return out;
    }
}

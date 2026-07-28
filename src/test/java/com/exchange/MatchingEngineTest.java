package com.exchange;

import com.exchange.domain.*;
import com.exchange.engine.Fill;
import com.exchange.engine.MatchingEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure price-time-priority matching, independent of Spring or settlement. */
class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();
    private long seq = 0;

    private Order order(String acct, Side side, OrderType type, long price, long qty) {
        return new Order("O-" + (++seq), seq, acct, "BTC-USD", side, type, price, qty);
    }

    @Test
    void crossingMarketBuyFillsRestingAskAtMakerPrice() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        Order ask = order("seller", Side.SELL, OrderType.LIMIT, 100, 10);
        engine.match(book, ask, 0);                       // rests

        Order buy = order("buyer", Side.BUY, OrderType.MARKET, 0, 4);
        List<Fill> fills = engine.match(book, buy, 1_000_000);

        assertEquals(1, fills.size());
        assertEquals(100, fills.get(0).price());          // trades at maker price
        assertEquals(4, fills.get(0).quantity());
        assertEquals(OrderStatus.FILLED, buy.status());
        assertEquals(6, ask.remainingQty());              // partially filled, still resting
        assertEquals(100L, book.bestAsk());
    }

    @Test
    void priceTimePriorityWithinLevel() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        Order first = order("s1", Side.SELL, OrderType.LIMIT, 100, 5);
        Order second = order("s2", Side.SELL, OrderType.LIMIT, 100, 5);
        engine.match(book, first, 0);
        engine.match(book, second, 0);

        Order buy = order("b", Side.BUY, OrderType.LIMIT, 100, 5);
        List<Fill> fills = engine.match(book, buy, 0);

        assertEquals(1, fills.size());
        assertEquals(first.id(), fills.get(0).maker().id());   // earlier order fills first
        assertEquals(OrderStatus.FILLED, first.status());
        assertEquals(OrderStatus.NEW, second.status());
    }

    @Test
    void bestPriceMatchesFirstAcrossLevels() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 101, 5), 0);
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 100, 5), 0);

        Order buy = order("b", Side.BUY, OrderType.MARKET, 0, 5);
        List<Fill> fills = engine.match(book, buy, 1_000_000);

        assertEquals(1, fills.size());
        assertEquals(100, fills.get(0).price());          // cheaper ask lifted first
    }

    @Test
    void nonCrossingLimitRestsWithoutTrading() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 105, 5), 0);

        Order buy = order("b", Side.BUY, OrderType.LIMIT, 100, 5);   // below best ask
        List<Fill> fills = engine.match(book, buy, 0);

        assertTrue(fills.isEmpty());
        assertEquals(100L, book.bestBid());               // now resting as a bid
        assertEquals(105L, book.bestAsk());
    }

    @Test
    void limitBuyWalksMultipleLevelsAndRestsRemainder() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 100, 3), 0);
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 101, 3), 0);

        Order buy = order("b", Side.BUY, OrderType.LIMIT, 101, 10);
        List<Fill> fills = engine.match(book, buy, 0);

        assertEquals(2, fills.size());
        assertEquals(6, buy.filledQty());
        assertEquals(4, buy.remainingQty());
        assertEquals(101L, book.bestBid());               // remainder rests at its limit
        assertNull(book.bestAsk());                       // both asks consumed
    }

    @Test
    void marketBuyStopsWhenBudgetExhausted() {
        OrderBook book = new OrderBook(Instrument.of("BTC-USD", "BTC", "USD"));
        engine.match(book, order("s", Side.SELL, OrderType.LIMIT, 100, 10), 0);

        Order buy = order("b", Side.BUY, OrderType.MARKET, 0, 10);
        List<Fill> fills = engine.match(book, buy, 350);  // affords only 3 @ 100

        assertEquals(1, fills.size());
        assertEquals(3, fills.get(0).quantity());
        assertEquals(3, buy.filledQty());
        assertEquals(7, buy.remainingQty());              // remainder will be cancelled by caller
    }
}

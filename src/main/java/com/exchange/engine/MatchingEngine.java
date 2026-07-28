package com.exchange.engine;

import com.exchange.domain.Order;
import com.exchange.domain.OrderBook;
import com.exchange.domain.OrderType;
import com.exchange.domain.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * The matching engine. Given an incoming <b>taker</b> order and a book, it
 * walks the opposite side from the best price and produces {@link Fill}s under
 * price-time priority, mutating order quantities and the book as it goes.
 *
 * <p>Trades always print at the resting <b>maker</b>'s price. A LIMIT order that
 * is not fully filled rests in the book; a MARKET order never rests and its
 * remainder is cancelled by the caller.
 *
 * <p>Settlement of funds is deliberately <b>not</b> done here — the engine only
 * knows quantities and prices. The one exception is that a MARKET BUY is capped
 * by a quote budget (the taker's available quote), because its cost is unknown
 * until it executes; this guarantees every returned fill is affordable.
 */
public class MatchingEngine {

    /**
     * @param book            the instrument's book
     * @param taker           the incoming order (mutated as it fills)
     * @param marketBuyBudget available quote for a MARKET BUY; ignored otherwise
     * @return the fills produced, in execution order
     */
    public List<Fill> match(OrderBook book, Order taker, long marketBuyBudget) {
        List<Fill> fills = new ArrayList<>();
        Side makerSide = (taker.side() == Side.BUY) ? Side.SELL : Side.BUY;
        long budgetLeft = marketBuyBudget;

        while (taker.remainingQty() > 0) {
            Order maker = book.peekBest(makerSide);
            if (maker == null) break;                       // no liquidity left
            if (!crosses(taker, maker)) break;              // price no longer crosses

            long price = maker.price();                     // trade at maker's price
            long qty = Math.min(taker.remainingQty(), maker.remainingQty());

            // MARKET BUY: cap by remaining quote budget.
            if (taker.type() == OrderType.MARKET && taker.side() == Side.BUY) {
                long affordable = price > 0 ? budgetLeft / price : 0;
                qty = Math.min(qty, affordable);
                if (qty == 0) break;                        // cannot afford another unit
            }

            maker.fill(qty);
            taker.fill(qty);
            fills.add(new Fill(maker, taker, price, qty));
            if (taker.type() == OrderType.MARKET && taker.side() == Side.BUY) {
                budgetLeft -= price * qty;
            }
            if (maker.remainingQty() == 0) book.pollBest(makerSide);
        }

        // A limit order with quantity left rests in the book.
        if (taker.type() == OrderType.LIMIT && taker.remainingQty() > 0) {
            book.rest(taker);
        }
        return fills;
    }

    /** Whether an incoming taker order can trade against a resting maker. */
    private boolean crosses(Order taker, Order maker) {
        if (taker.type() == OrderType.MARKET) return true;
        return taker.side() == Side.BUY
                ? taker.price() >= maker.price()            // buy lifts asks at/below limit
                : taker.price() <= maker.price();           // sell hits bids at/above limit
    }
}

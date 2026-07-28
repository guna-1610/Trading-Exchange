package com.exchange.service;

import com.exchange.domain.*;
import com.exchange.dto.*;
import com.exchange.engine.Fill;
import com.exchange.engine.MatchingEngine;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The exchange. Owns one {@link OrderBook} per instrument and serialises all
 * activity on a given instrument under a per-symbol lock, so matching and
 * settlement for that book are effectively single-threaded while different
 * symbols run in parallel. Account balances are mutated through atomic,
 * per-account synchronised operations, and the service never holds two account
 * locks at once — so there is no deadlock.
 *
 * <p><b>Fund flow.</b> Placing an order reserves the funds it could spend
 * (quote for a limit buy, base for any sell). Each fill settles atomically:
 * the buyer's reserved/available quote is spent and base credited; the seller's
 * reserved base is spent and quote credited. A limit buy that executes below its
 * limit has the difference refunded. Market buys spend available quote directly,
 * capped by a budget the engine enforces. The net effect of any trade is a pure
 * transfer between two accounts — total holdings of every asset are conserved.
 */
@Service
public class ExchangeService {

    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final MatchingEngine engine = new MatchingEngine();

    private final AccountService accountService;
    private final OrderRepository orderRepo;
    private final TradeRepository tradeRepo;
    private final MarketDataBroadcaster broadcaster;

    private final AtomicLong orderSeq = new AtomicLong(0);
    private final AtomicLong tradeSeq = new AtomicLong(0);

    public ExchangeService(AccountService accountService, OrderRepository orderRepo,
                           TradeRepository tradeRepo, MarketDataBroadcaster broadcaster) {
        this.accountService = accountService;
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    void seedInstruments() {
        registerInstrument("BTC-USD", "BTC", "USD");
        registerInstrument("ETH-USD", "ETH", "USD");
    }

    public void registerInstrument(String symbol, String base, String quote) {
        Instrument i = Instrument.of(symbol, base, quote);
        instruments.put(symbol, i);
        books.put(symbol, new OrderBook(i));
        locks.put(symbol, new ReentrantLock());
    }

    public Collection<Instrument> instruments() { return instruments.values(); }

    // ----- order placement -------------------------------------------------

    public PlaceOrderResponse placeOrder(String accountId, String symbol, Side side,
                                         OrderType type, long price, long quantity) {
        Instrument inst = instruments.get(symbol);
        if (inst == null) throw new RejectedException("unknown symbol: " + symbol);
        if (quantity <= 0) throw new RejectedException("quantity must be positive");
        if (type == OrderType.LIMIT && price <= 0) throw new RejectedException("limit price must be positive");
        Account account = accountService.get(accountId);           // throws if missing
        long orderPrice = (type == OrderType.MARKET) ? 0 : price;

        ReentrantLock lock = locks.get(symbol);
        lock.lock();
        try {
            OrderBook book = books.get(symbol);
            Order order = new Order("O-" + orderSeq.incrementAndGet(), orderSeq.get(),
                    accountId, symbol, side, type, orderPrice, quantity);
            orderRepo.save(order);

            // Reserve funds (or compute market-buy budget).
            long marketBuyBudget = 0;
            if (type == OrderType.LIMIT && side == Side.BUY) {
                if (!account.reserve(inst.quote(), quantity * price)) {
                    order.reject();
                    throw new RejectedException("insufficient " + inst.quote() + " to reserve "
                            + (quantity * price));
                }
            } else if (side == Side.SELL) {  // limit or market sell: reserve base
                if (!account.reserve(inst.base(), quantity)) {
                    order.reject();
                    throw new RejectedException("insufficient " + inst.base() + " to reserve " + quantity);
                }
            } else {                          // market buy: budget = available quote
                marketBuyBudget = account.available(inst.quote());
            }

            // Match.
            List<Fill> fills = engine.match(book, order, marketBuyBudget);

            // Settle each fill and record trades.
            List<TradeView> trades = new ArrayList<>();
            for (Fill f : fills) {
                Trade t = settle(f, inst, order.side());
                trades.add(TradeView.of(t));
            }

            // Handle unfilled remainder of a market order (never rests).
            if (type == OrderType.MARKET && order.remainingQty() > 0) {
                if (side == Side.SELL) account.release(inst.base(), order.remainingQty());
                order.cancel();               // remainder cancelled; filledQty is preserved
            }

            broadcastMarket(symbol);
            return new PlaceOrderResponse(OrderView.of(order), trades);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Apply one fill atomically. Buyer pays quote and receives base; seller
     * receives quote and gives base. Which pot the money leaves (reserved vs
     * available) depends on whether the account was maker or taker.
     */
    private Trade settle(Fill f, Instrument inst, Side takerSide) {
        Order maker = f.maker(), taker = f.taker();
        long qty = f.quantity(), notional = f.notional();
        String base = inst.base(), quote = inst.quote();

        Order buyOrder  = (taker.side() == Side.BUY) ? taker : maker;
        Order sellOrder = (taker.side() == Side.BUY) ? maker : taker;
        Account buyer  = accountService.get(buyOrder.accountId());
        Account seller = accountService.get(sellOrder.accountId());

        // Seller always reserved base up front (limit or market sell).
        seller.spendReserved(base, qty);
        seller.credit(quote, notional);

        // Buyer.
        if (buyOrder == maker) {                       // resting limit buy: reserved at its own price
            buyer.spendReserved(quote, notional);
        } else if (taker.type() == OrderType.LIMIT) {  // taker limit buy: refund any over-reservation
            buyer.spendReserved(quote, notional);
            long refund = (taker.price() - f.price()) * qty;   // >= 0 since it crossed
            if (refund > 0) buyer.release(quote, refund);
        } else {                                       // taker market buy: pay from available (budgeted)
            buyer.spendAvailable(quote, notional);
        }
        buyer.credit(base, qty);

        Trade t = new Trade(tradeSeq.incrementAndGet(), inst.symbol(), f.price(), qty,
                maker.id(), taker.id(), buyer.id(), seller.id(), takerSide, Instant.now());
        tradeRepo.save(t);
        broadcaster.broadcast("trade", TradeView.of(t));
        return t;
    }

    // ----- cancellation -----------------------------------------------------

    public OrderView cancel(String orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("order not found: " + orderId));
        ReentrantLock lock = locks.get(order.symbol());
        lock.lock();
        try {
            if (!order.isActive()) throw new RejectedException("order not active: " + order.status());
            Instrument inst = instruments.get(order.symbol());
            books.get(order.symbol()).remove(order);
            // Release funds still reserved against the remaining quantity.
            if (order.side() == Side.BUY) {
                accountService.get(order.accountId()).release(inst.quote(), order.remainingQty() * order.price());
            } else {
                accountService.get(order.accountId()).release(inst.base(), order.remainingQty());
            }
            order.cancel();
            broadcastMarket(order.symbol());
            return OrderView.of(order);
        } finally {
            lock.unlock();
        }
    }

    // ----- queries ----------------------------------------------------------

    public OrderView getOrder(String orderId) {
        return OrderView.of(orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("order not found: " + orderId)));
    }

    public OrderBookView bookView(String symbol, int levels) {
        Instrument inst = instruments.get(symbol);
        if (inst == null) throw new NotFoundException("unknown symbol: " + symbol);
        ReentrantLock lock = locks.get(symbol);
        lock.lock();
        try {
            OrderBook book = books.get(symbol);
            List<DepthLevel> bids = new ArrayList<>();
            for (long[] lv : book.depth(Side.BUY, levels)) bids.add(new DepthLevel(lv[0], lv[1]));
            List<DepthLevel> asks = new ArrayList<>();
            for (long[] lv : book.depth(Side.SELL, levels)) asks.add(new DepthLevel(lv[0], lv[1]));
            Long bb = book.bestBid(), ba = book.bestAsk();
            Long spread = (bb != null && ba != null) ? ba - bb : null;
            return new OrderBookView(symbol, bb, ba, spread, bids, asks);
        } finally {
            lock.unlock();
        }
    }

    public List<TradeView> trades(String symbol, int limit) {
        List<TradeView> out = new ArrayList<>();
        for (Trade t : tradeRepo.recent(symbol, limit)) out.add(TradeView.of(t));
        return out;
    }

    private void broadcastMarket(String symbol) {
        broadcaster.broadcast("book", bookView(symbol, 10));
    }
}

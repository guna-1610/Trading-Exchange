package com.exchange;

import com.exchange.domain.Account;
import com.exchange.domain.OrderType;
import com.exchange.domain.Side;
import com.exchange.dto.PlaceOrderResponse;
import com.exchange.repository.AccountRepository;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import com.exchange.service.AccountService;
import com.exchange.service.ExchangeService;
import com.exchange.service.RejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fund reservation, atomic settlement, and balance correctness. */
class ExchangeServiceTest {

    private AccountService accountService;
    private ExchangeService exchange;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(new AccountRepository());
        exchange = new ExchangeService(accountService, new OrderRepository(),
                new TradeRepository(), (topic, payload) -> { });
        exchange.registerInstrument("BTC-USD", "BTC", "USD");
    }

    private Account funded(String name, long usd, long btc) {
        Account a = accountService.create(name);
        if (usd > 0) a.deposit("USD", usd);
        if (btc > 0) a.deposit("BTC", btc);
        return a;
    }

    @Test
    void limitSellReservesBaseUntilMatched() {
        Account seller = funded("seller", 0, 10);
        exchange.placeOrder(seller.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 100, 4);
        assertEquals(6, seller.available("BTC"));
        assertEquals(4, seller.reserved("BTC"));
    }

    @Test
    void matchedTradeSettlesBothSides() {
        Account seller = funded("seller", 0, 10);
        Account buyer = funded("buyer", 1_000, 0);
        exchange.placeOrder(seller.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 100, 5);
        PlaceOrderResponse res = exchange.placeOrder(buyer.id(), "BTC-USD", Side.BUY, OrderType.LIMIT, 100, 5);

        assertEquals(1, res.trades().size());
        // seller: gave 5 BTC, received 500 USD
        assertEquals(5, seller.available("BTC"));
        assertEquals(0, seller.reserved("BTC"));
        assertEquals(500, seller.available("USD"));
        // buyer: paid 500 USD, received 5 BTC
        assertEquals(500, buyer.available("USD"));
        assertEquals(5, buyer.available("BTC"));
    }

    @Test
    void limitBuyBelowFillPriceRefundsOverReservation() {
        Account seller = funded("seller", 0, 10);
        Account buyer = funded("buyer", 1_000, 0);
        exchange.placeOrder(seller.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 90, 5);
        // buyer bids up to 100 but trades at the maker's 90
        exchange.placeOrder(buyer.id(), "BTC-USD", Side.BUY, OrderType.LIMIT, 100, 5);

        assertEquals(5, buyer.available("BTC"));
        assertEquals(550, buyer.available("USD"));       // 1000 - 5*90, over-reservation refunded
        assertEquals(0, buyer.reserved("USD"));
    }

    @Test
    void insufficientFundsRejectsOrder() {
        Account buyer = funded("buyer", 100, 0);
        RejectedException ex = assertThrows(RejectedException.class, () ->
                exchange.placeOrder(buyer.id(), "BTC-USD", Side.BUY, OrderType.LIMIT, 100, 5)); // needs 500
        assertTrue(ex.getMessage().contains("insufficient"));
        assertEquals(100, buyer.available("USD"));        // nothing reserved
    }

    @Test
    void cancelReleasesReservedFunds() {
        Account buyer = funded("buyer", 1_000, 0);
        var res = exchange.placeOrder(buyer.id(), "BTC-USD", Side.BUY, OrderType.LIMIT, 100, 5);
        assertEquals(500, buyer.reserved("USD"));
        exchange.cancel(res.order().id());
        assertEquals(1_000, buyer.available("USD"));
        assertEquals(0, buyer.reserved("USD"));
    }

    @Test
    void marketBuyStopsAtAvailableFunds() {
        Account seller = funded("seller", 0, 10);
        Account buyer = funded("buyer", 350, 0);          // affords 3 @ 100
        exchange.placeOrder(seller.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 100, 10);
        var res = exchange.placeOrder(buyer.id(), "BTC-USD", Side.BUY, OrderType.MARKET, 0, 10);

        assertEquals(3, buyer.available("BTC"));          // could only afford 3
        assertEquals(50, buyer.available("USD"));         // 350 - 300
        assertEquals("CANCELLED", res.order().status());  // remainder cancelled
    }
}

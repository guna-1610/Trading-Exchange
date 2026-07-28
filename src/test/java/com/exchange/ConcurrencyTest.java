package com.exchange;

import com.exchange.domain.Account;
import com.exchange.domain.OrderType;
import com.exchange.domain.Side;
import com.exchange.repository.AccountRepository;
import com.exchange.repository.OrderRepository;
import com.exchange.repository.TradeRepository;
import com.exchange.service.AccountService;
import com.exchange.service.ExchangeService;
import com.exchange.service.RejectedException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fires thousands of orders concurrently and asserts the exchange's central
 * invariant: matching only ever <b>transfers</b> assets between accounts, so the
 * total quantity of every asset (available + reserved, summed over all accounts)
 * is exactly conserved. Any lost update, double-spend, or deadlock would break
 * this or hang the test.
 */
class ConcurrencyTest {

    @Test
    void totalValueIsConservedUnderConcurrentTrading() throws Exception {
        AccountService accountService = new AccountService(new AccountRepository());
        ExchangeService exchange = new ExchangeService(accountService, new OrderRepository(),
                new TradeRepository(), (topic, payload) -> { });
        exchange.registerInstrument("BTC-USD", "BTC", "USD");

        int nAccounts = 8;
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < nAccounts; i++) {
            Account a = accountService.create("acct-" + i);
            a.deposit("USD", 10_000_000);
            a.deposit("BTC", 1_000);
            accounts.add(a);
        }

        long usdBefore = accounts.stream().mapToLong(a -> a.total("USD")).sum();
        long btcBefore = accounts.stream().mapToLong(a -> a.total("BTC")).sum();

        int threads = 8, opsPerThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        Account acct = accounts.get(rnd.nextInt(nAccounts));
                        Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                        OrderType type = rnd.nextInt(4) == 0 ? OrderType.MARKET : OrderType.LIMIT;
                        long price = 29_000 + rnd.nextInt(2_000);   // straddle the book
                        long qty = 1 + rnd.nextInt(5);
                        try {
                            exchange.placeOrder(acct.id(), "BTC-USD", side, type, price, qty);
                        } catch (RejectedException ignored) {
                            // expected when an account lacks funds/inventory
                        }
                    }
                } catch (Throwable th) {
                    failure.compareAndSet(null, th);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "trading did not finish — possible deadlock");
        pool.shutdownNow();
        if (failure.get() != null) fail("unexpected error during concurrent trading", failure.get());

        long usdAfter = accounts.stream().mapToLong(a -> a.total("USD")).sum();
        long btcAfter = accounts.stream().mapToLong(a -> a.total("BTC")).sum();

        assertEquals(usdBefore, usdAfter, "USD was created or destroyed");
        assertEquals(btcBefore, btcAfter, "BTC was created or destroyed");

        // And no account is ever negative.
        for (Account a : accounts) {
            assertTrue(a.available("USD") >= 0 && a.reserved("USD") >= 0);
            assertTrue(a.available("BTC") >= 0 && a.reserved("BTC") >= 0);
        }
    }
}

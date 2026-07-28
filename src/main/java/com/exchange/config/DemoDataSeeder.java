package com.exchange.config;

import com.exchange.domain.OrderType;
import com.exchange.domain.Side;
import com.exchange.service.AccountService;
import com.exchange.service.AuthService;
import com.exchange.service.ExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a starting BTC-USD book (via internal market-maker accounts) and a ready
 * demo login (demo / password123) so the app is usable immediately. Disabled
 * under the "test" profile.
 */
@Component
@Profile("!test")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AccountService accounts;
    private final ExchangeService exchange;
    private final AuthService auth;

    public DemoDataSeeder(AccountService accounts, ExchangeService exchange, AuthService auth) {
        this.accounts = accounts;
        this.exchange = exchange;
        this.auth = auth;
    }

    @Override
    public void run(String... args) {
        // Internal market-maker accounts provide starting liquidity (not login users).
        var mmA = accounts.create("MarketMaker-A");
        var mmB = accounts.create("MarketMaker-B");
        accounts.deposit(mmA.id(), "USD", 5_000_000);
        accounts.deposit(mmA.id(), "BTC", 50);
        accounts.deposit(mmB.id(), "USD", 5_000_000);
        accounts.deposit(mmB.id(), "BTC", 50);

        exchange.placeOrder(mmA.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 30_200, 5);
        exchange.placeOrder(mmA.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 30_100, 3);
        exchange.placeOrder(mmB.id(), "BTC-USD", Side.SELL, OrderType.LIMIT, 30_050, 2);
        exchange.placeOrder(mmB.id(), "BTC-USD", Side.BUY,  OrderType.LIMIT, 29_950, 2);
        exchange.placeOrder(mmA.id(), "BTC-USD", Side.BUY,  OrderType.LIMIT, 29_900, 4);
        exchange.placeOrder(mmB.id(), "BTC-USD", Side.BUY,  OrderType.LIMIT, 29_800, 6);

        // A ready-to-use demo login.
        try {
            auth.register("demo", "password123", "demo@example.com");
            log.info("Seeded BTC-USD book and demo login (username: demo / password: password123)");
        } catch (RuntimeException e) {
            log.info("Seeded BTC-USD book; demo user already present");
        }
    }
}

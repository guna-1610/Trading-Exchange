package com.exchange.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An account holding balances in one or more assets. Each asset has an
 * {@code available} balance (spendable) and a {@code reserved} balance (held
 * against open orders). Total holdings = available + reserved.
 *
 * <p>All mutations are {@code synchronized} on this account, so individual
 * debit / credit / reserve operations are atomic under concurrent trading. The
 * exchange never holds two account locks at once, so there is no deadlock.
 */
public class Account {

    /** Available and reserved amounts for a single asset. */
    public static final class Balance {
        long available;
        long reserved;
        Balance(long available) { this.available = available; }
        public long available() { return available; }
        public long reserved()  { return reserved; }
        public long total()     { return available + reserved; }
    }

    private final String id;
    private final String name;
    private final Map<String, Balance> balances = new ConcurrentHashMap<>();

    public Account(String id, String name) {
        this.id = id;
        this.name = name;
    }

    private Balance bal(String asset) {
        return balances.computeIfAbsent(asset, a -> new Balance(0));
    }

    public synchronized void deposit(String asset, long amount) {
        require(amount > 0, "deposit amount must be positive");
        bal(asset).available += amount;
    }

    /** Move {@code amount} from available to reserved; false if insufficient. */
    public synchronized boolean reserve(String asset, long amount) {
        require(amount > 0, "reserve amount must be positive");
        Balance b = bal(asset);
        if (b.available < amount) return false;
        b.available -= amount;
        b.reserved += amount;
        return true;
    }

    /** Release reserved funds back to available (e.g. on cancel or over-reserve refund). */
    public synchronized void release(String asset, long amount) {
        if (amount <= 0) return;
        Balance b = bal(asset);
        require(b.reserved >= amount, "release exceeds reserved");
        b.reserved -= amount;
        b.available += amount;
    }

    /** Spend previously reserved funds (they leave the account on settlement). */
    public synchronized void spendReserved(String asset, long amount) {
        if (amount <= 0) return;
        Balance b = bal(asset);
        require(b.reserved >= amount, "spendReserved exceeds reserved");
        b.reserved -= amount;
    }

    /** Spend directly from available (used by market buys that pre-checked funds). */
    public synchronized boolean spendAvailable(String asset, long amount) {
        require(amount > 0, "spend amount must be positive");
        Balance b = bal(asset);
        if (b.available < amount) return false;
        b.available -= amount;
        return true;
    }

    /** Credit settled proceeds into available. */
    public synchronized void credit(String asset, long amount) {
        if (amount <= 0) return;
        bal(asset).available += amount;
    }

    public synchronized long available(String asset) { return bal(asset).available; }
    public synchronized long reserved(String asset)  { return bal(asset).reserved; }
    public synchronized long total(String asset)     { return bal(asset).total(); }

    public Map<String, Balance> balancesView() { return balances; }
    public String id() { return id; }
    public String name() { return name; }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new IllegalArgumentException(msg);
    }
}

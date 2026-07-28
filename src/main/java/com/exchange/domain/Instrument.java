package com.exchange.domain;

/**
 * A tradable market, e.g. BTC-USD, with a base asset (BTC) bought/sold and a
 * quote asset (USD) it is priced in. All prices and quantities are integers in
 * the asset's smallest tradable unit, so notional = price * quantity is exact
 * (no floating-point money bugs).
 */
public record Instrument(String symbol, String base, String quote) {
    public static Instrument of(String symbol, String base, String quote) {
        return new Instrument(symbol, base, quote);
    }
}

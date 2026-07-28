package com.exchange.service;

/**
 * Sink for real-time market-data events. Implemented by the WebSocket layer;
 * the exchange calls it after every book mutation and trade. Kept as an
 * interface so the core service has no dependency on the transport (and so unit
 * tests can pass a no-op).
 */
public interface MarketDataBroadcaster {
    void broadcast(String topic, Object payload);
}

package com.exchange.service;

/** Thrown when an order is rejected (bad input, unknown market, insufficient funds). */
public class RejectedException extends RuntimeException {
    public RejectedException(String message) { super(message); }
}

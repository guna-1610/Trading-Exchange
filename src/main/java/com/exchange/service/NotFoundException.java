package com.exchange.service;

/** Thrown when a requested entity (account, order, instrument) does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}

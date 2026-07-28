package com.exchange.domain;

/**
 * LIMIT  — rests in the book at a stated price if not immediately matched.
 * MARKET — takes liquidity at the best available price; never rests. Any
 *          unfilled remainder is cancelled.
 */
public enum OrderType { LIMIT, MARKET }

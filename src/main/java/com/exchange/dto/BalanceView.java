package com.exchange.dto;

public record BalanceView(String asset, long available, long reserved, long total) {}

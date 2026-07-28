package com.exchange.dto;

import java.util.List;

public record AccountView(String id, String name, List<BalanceView> balances) {}

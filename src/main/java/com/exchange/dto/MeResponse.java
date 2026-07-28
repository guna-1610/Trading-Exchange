package com.exchange.dto;

import java.util.List;

public record MeResponse(String username, String email, String accountId, List<BalanceView> balances) {}

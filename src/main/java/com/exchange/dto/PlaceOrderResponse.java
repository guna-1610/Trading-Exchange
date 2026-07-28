package com.exchange.dto;

import java.util.List;

public record PlaceOrderResponse(OrderView order, List<TradeView> trades) {}

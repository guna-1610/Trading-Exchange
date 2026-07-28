package com.exchange.dto;

import java.util.List;

public record OrderBookView(String symbol, Long bestBid, Long bestAsk, Long spread,
                            List<DepthLevel> bids, List<DepthLevel> asks) {}

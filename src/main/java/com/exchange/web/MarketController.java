package com.exchange.web;

import com.exchange.domain.Instrument;
import com.exchange.dto.InstrumentView;
import com.exchange.dto.OrderBookView;
import com.exchange.dto.TradeView;
import com.exchange.service.ExchangeService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final ExchangeService exchange;

    public MarketController(ExchangeService exchange) { this.exchange = exchange; }

    @GetMapping("/instruments")
    public List<InstrumentView> instruments() {
        List<InstrumentView> out = new ArrayList<>();
        for (Instrument i : exchange.instruments())
            out.add(new InstrumentView(i.symbol(), i.base(), i.quote()));
        return out;
    }

    @GetMapping("/{symbol}/book")
    public OrderBookView book(@PathVariable String symbol,
                              @RequestParam(defaultValue = "10") int levels) {
        return exchange.bookView(symbol, levels);
    }

    @GetMapping("/{symbol}/trades")
    public List<TradeView> trades(@PathVariable String symbol,
                                  @RequestParam(defaultValue = "50") int limit) {
        return exchange.trades(symbol, limit);
    }
}

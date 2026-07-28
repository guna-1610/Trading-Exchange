package com.exchange.repository;

import com.exchange.domain.Trade;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory trade tape. Append-only; reads return most-recent-first. */
@Repository
public class TradeRepository {
    private final CopyOnWriteArrayList<Trade> trades = new CopyOnWriteArrayList<>();

    public Trade save(Trade t) { trades.add(t); return t; }

    public List<Trade> recent(String symbol, int limit) {
        List<Trade> out = new ArrayList<>();
        for (int i = trades.size() - 1; i >= 0 && out.size() < limit; i--) {
            Trade t = trades.get(i);
            if (symbol == null || t.symbol().equals(symbol)) out.add(t);
        }
        return out;
    }

    public List<Trade> all() { return Collections.unmodifiableList(trades); }
}

package com.exchange.repository;

import com.exchange.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory order store (all orders ever accepted, for status lookup). */
@Repository
public class OrderRepository {
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order o) { orders.put(o.id(), o); return o; }
    public Optional<Order> findById(String id) { return Optional.ofNullable(orders.get(id)); }
    public Collection<Order> findAll() { return orders.values(); }
}

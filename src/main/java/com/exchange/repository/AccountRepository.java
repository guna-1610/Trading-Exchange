package com.exchange.repository;

import com.exchange.domain.Account;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory account store. Swap this for a JPA implementation to persist. */
@Repository
public class AccountRepository {
    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    public Account save(Account a) { accounts.put(a.id(), a); return a; }
    public Optional<Account> findById(String id) { return Optional.ofNullable(accounts.get(id)); }
    public Collection<Account> findAll() { return accounts.values(); }
    public boolean exists(String id) { return accounts.containsKey(id); }
}

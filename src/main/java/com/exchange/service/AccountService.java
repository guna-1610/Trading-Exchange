package com.exchange.service;

import com.exchange.domain.Account;
import com.exchange.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AtomicLong seq = new AtomicLong(1000);

    public AccountService(AccountRepository accounts) { this.accounts = accounts; }

    public Account create(String name) {
        String id = "A-" + seq.incrementAndGet();
        return accounts.save(new Account(id, name == null || name.isBlank() ? id : name));
    }

    public Account get(String id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("account not found: " + id));
    }

    public void deposit(String id, String asset, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("deposit amount must be positive");
        get(id).deposit(asset, amount);
    }

    public Collection<Account> all() { return accounts.findAll(); }
}

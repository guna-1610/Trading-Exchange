package com.exchange.web;

import com.exchange.domain.Account;
import com.exchange.dto.*;
import com.exchange.service.AccountService;
import com.exchange.service.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    private static String accountId(Jwt jwt) { return jwt.getClaimAsString("accountId"); }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        requireOwn(id, jwt);
        return view(accounts.get(id));
    }

    /** Demo faucet — top up your own account. */
    @PostMapping("/{id}/deposits")
    public AccountView deposit(@PathVariable String id, @Valid @RequestBody DepositRequest req,
                               @AuthenticationPrincipal Jwt jwt) {
        requireOwn(id, jwt);
        accounts.deposit(id, req.asset(), req.amount());
        return view(accounts.get(id));
    }

    private void requireOwn(String id, Jwt jwt) {
        if (!id.equals(accountId(jwt))) throw new NotFoundException("account not found: " + id);
    }

    private static AccountView view(Account a) {
        List<BalanceView> balances = new ArrayList<>();
        a.balancesView().forEach((asset, b) ->
                balances.add(new BalanceView(asset, b.available(), b.reserved(), b.total())));
        return new AccountView(a.id(), a.name(), balances);
    }
}

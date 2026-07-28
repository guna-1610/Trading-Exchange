package com.exchange.service;

import com.exchange.domain.Account;
import com.exchange.domain.User;
import com.exchange.dto.*;
import com.exchange.repository.UserRepository;
import com.exchange.security.JwtService;
import com.exchange.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles registration and login. On first sign-in (either method) a user gets
 * a fresh trading {@link Account} seeded with demo funds so they can trade
 * immediately.
 */
@Service
public class AuthService {

    private static final long FAUCET_USD = 1_000_000;
    private static final long FAUCET_BTC = 10;

    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JwtDecoder googleDecoder;
    private final SecurityConfig security;
    private final AtomicLong userSeq = new AtomicLong(0);

    public AuthService(UserRepository users, AccountService accounts, PasswordEncoder passwordEncoder,
                       JwtService jwt, @Qualifier("googleJwtDecoder") JwtDecoder googleDecoder,
                       SecurityConfig security) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.googleDecoder = googleDecoder;
        this.security = security;
    }

    private Account newFundedAccount(String name) {
        Account a = accounts.create(name);
        accounts.deposit(a.id(), "USD", FAUCET_USD);
        accounts.deposit(a.id(), "BTC", FAUCET_BTC);
        return a;
    }

    public AuthResponse register(String username, String password, String email) {
        if (users.usernameExists(username))
            throw new RejectedException("username already taken: " + username);
        Account account = newFundedAccount(username);
        User u = new User("U-" + userSeq.incrementAndGet(), username, email,
                passwordEncoder.encode(password), null, account.id());
        users.save(u);
        return new AuthResponse(jwt.issue(u.username(), u.accountId()), u.username(), u.accountId(), "password");
    }

    public AuthResponse login(String username, String password) {
        User u = users.findByUsername(username)
                .filter(x -> x.passwordHash() != null)
                .filter(x -> passwordEncoder.matches(password, x.passwordHash()))
                .orElseThrow(() -> new RejectedException("invalid username or password"));
        return new AuthResponse(jwt.issue(u.username(), u.accountId()), u.username(), u.accountId(), "password");
    }

    /** Verify a Google ID token and log the user in, creating them on first sign-in. */
    public AuthResponse googleLogin(String credential) {
        if (!security.googleEnabled())
            throw new RejectedException("Google login is not configured on this server");
        Jwt token;
        try {
            token = googleDecoder.decode(credential);
        } catch (JwtException e) {
            throw new RejectedException("invalid Google credential: " + e.getMessage());
        }
        String sub = token.getSubject();
        String email = token.getClaimAsString("email");
        String name = token.getClaimAsString("name");

        User u = users.findByGoogleSub(sub).orElse(null);
        if (u == null && email != null) {                 // link Google to an existing email account
            u = users.findByEmail(email).orElse(null);
            if (u != null) u.setGoogleSub(sub);
        }
        if (u == null) {                                   // brand-new Google user
            String username = deriveUsername(email, name, sub);
            Account account = newFundedAccount(username);
            u = new User("U-" + userSeq.incrementAndGet(), username, email, null, sub, account.id());
        }
        users.save(u);
        return new AuthResponse(jwt.issue(u.username(), u.accountId()), u.username(), u.accountId(), "google");
    }

    private String deriveUsername(String email, String name, String sub) {
        String base = email != null ? email.substring(0, email.indexOf('@'))
                : (name != null ? name.replaceAll("\\s+", "").toLowerCase() : "user");
        String candidate = base;
        int n = 1;
        while (users.usernameExists(candidate)) candidate = base + (++n);
        return candidate;
    }

    public MeResponse me(String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("user not found: " + username));
        Account a = accounts.get(u.accountId());
        List<BalanceView> balances = new ArrayList<>();
        a.balancesView().forEach((asset, b) ->
                balances.add(new BalanceView(asset, b.available(), b.reserved(), b.total())));
        return new MeResponse(u.username(), u.email(), u.accountId(), balances);
    }

    public AuthConfigResponse config() {
        return new AuthConfigResponse(security.googleEnabled(), security.googleClientId());
    }
}

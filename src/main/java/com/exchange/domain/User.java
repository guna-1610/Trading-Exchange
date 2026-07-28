package com.exchange.domain;

import java.time.Instant;

/**
 * An authenticated user of the exchange. Each user is linked to exactly one
 * trading {@link Account}. A user may authenticate with a username/password
 * (in which case {@code passwordHash} is set) or via Google (in which case
 * {@code googleSub} — Google's stable subject id — is set). The two can coexist.
 */
public class User {

    private final String id;
    private String username;
    private String email;
    private String passwordHash;   // BCrypt; null for Google-only accounts
    private String googleSub;      // Google subject id; null for password-only
    private final String accountId;
    private final Instant createdAt;

    public User(String id, String username, String email, String passwordHash,
                String googleSub, String accountId) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleSub = googleSub;
        this.accountId = accountId;
        this.createdAt = Instant.now();
    }

    public String id() { return id; }
    public String username() { return username; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String googleSub() { return googleSub; }
    public String accountId() { return accountId; }
    public Instant createdAt() { return createdAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public void setEmail(String email) { this.email = email; }
}

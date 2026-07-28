package com.exchange.repository;

import com.exchange.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory user store, keyed by id with username / googleSub / email indexes. */
@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, User> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> usernameToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> googleSubToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> emailToId = new ConcurrentHashMap<>();

    public synchronized User save(User u) {
        byId.put(u.id(), u);
        if (u.username() != null) usernameToId.put(u.username().toLowerCase(), u.id());
        if (u.googleSub() != null) googleSubToId.put(u.googleSub(), u.id());
        if (u.email() != null) emailToId.put(u.email().toLowerCase(), u.id());
        return u;
    }

    public Optional<User> findById(String id) { return Optional.ofNullable(byId.get(id)); }

    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String id = usernameToId.get(username.toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }

    public Optional<User> findByGoogleSub(String sub) {
        if (sub == null) return Optional.empty();
        String id = googleSubToId.get(sub);
        return id == null ? Optional.empty() : findById(id);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String id = emailToId.get(email.toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }

    public boolean usernameExists(String username) {
        return username != null && usernameToId.containsKey(username.toLowerCase());
    }
}

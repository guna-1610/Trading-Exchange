package com.exchange.dto;

/** Returned on successful register/login: a bearer token plus who you are. */
public record AuthResponse(String token, String username, String accountId, String authProvider) {}

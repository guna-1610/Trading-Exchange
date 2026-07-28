package com.exchange.dto;

/** Public auth configuration for the frontend (e.g. whether Google login is enabled). */
public record AuthConfigResponse(boolean googleEnabled, String googleClientId) {}

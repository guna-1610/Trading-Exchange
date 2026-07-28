package com.exchange.dto;

import jakarta.validation.constraints.NotBlank;

/** The Google ID token (a JWT) returned by Google Identity Services on the client. */
public record GoogleLoginRequest(@NotBlank String credential) {}

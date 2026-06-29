package com.documind.contracts;

import jakarta.validation.constraints.NotBlank;

/** Credentials posted to {@code POST /auth/login} on the gateway. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}

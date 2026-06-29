package com.documind.contracts;

/** Uniform error body returned by every service's exception handlers. */
public record ErrorResponse(
        String error,
        String message
) {
}

package com.documind.contracts;

/**
 * The JWT returned by a successful login. Serialized as
 * {@code {access_token, token_type}} to match the React auth client.
 */
public record TokenResponse(
        String accessToken,
        String tokenType
) {
}

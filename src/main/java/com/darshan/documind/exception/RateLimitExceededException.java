package com.darshan.documind.exception;

/** Thrown by the rate-limit interceptor; maps to 429 Too Many Requests. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}

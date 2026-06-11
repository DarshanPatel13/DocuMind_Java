package com.org.documind.config;

import com.org.documind.exception.RateLimitExceededException;
import com.org.documind.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the per-IP limit before the controller runs. Registered for
 * /api/ask only (see WebConfig) — that is the endpoint that costs LLM money
 * per hit; uploads and reads don't need protecting at this scale.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitService.tryAcquire(clientIp(request))) {
            // Surfaced as a 429 JSON body by GlobalExceptionHandler.
            throw new RateLimitExceededException("Rate limit exceeded for /api/ask — try again in a minute");
        }
        return true;
    }

    /** Honour X-Forwarded-For when behind a proxy; fall back to the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}

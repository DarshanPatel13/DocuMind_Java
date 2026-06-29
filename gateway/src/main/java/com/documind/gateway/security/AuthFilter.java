package com.documind.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Front-of-chain reactive filter: (1) assigns/propagates the {@code X-Request-ID}
 * correlation id downstream, and (2) requires a valid JWT on {@code /api/**}.
 * Public paths ({@code /auth/**}, {@code /actuator/**}, {@code /health}) and CORS
 * preflight ({@code OPTIONS}) pass through. The authenticated username is stashed
 * as an exchange attribute for the rate limiter.
 */
@Component
public class AuthFilter implements WebFilter, Ordered {

    public static final String USER_ATTR = "documind.user";
    private static final String REQUEST_ID = "X-Request-ID";

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Correlation id: reuse the inbound one or mint a new one, forward downstream + echo back.
        String requestId = request.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        ServerHttpRequest mutated = request.mutate().header(REQUEST_ID, requestId).build();
        exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
        ServerWebExchange forwarded = exchange.mutate().request(mutated).build();

        String path = request.getPath().value();
        boolean isPublic = path.startsWith("/auth/") || path.startsWith("/actuator") || path.equals("/health")
                || request.getMethod() == HttpMethod.OPTIONS;
        if (isPublic || !path.startsWith("/api/")) {
            return chain.filter(forwarded);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(forwarded);
        }
        try {
            String username = jwtService.validateAndGetSubject(authorization.substring(7));
            forwarded.getAttributes().put(USER_ATTR, username);
        } catch (Exception e) {
            return unauthorized(forwarded);
        }
        return chain.filter(forwarded);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;   // before routing + rate limiting
    }
}

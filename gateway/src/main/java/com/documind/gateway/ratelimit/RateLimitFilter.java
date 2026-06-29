package com.documind.gateway.ratelimit;

import com.documind.gateway.security.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis fixed-window rate limiter on the expensive {@code /api/ask} (one key per
 * user+minute, INCR with a 60s TTL, reject over the limit). A shared Redis
 * counter stays correct across gateway replicas. Fails OPEN if Redis is down.
 * Java equivalent of the Python {@code rate_limit.py}.
 */
@Component
public class RateLimitFilter implements WebFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;
    private final int limitPerMinute;

    public RateLimitFilter(ReactiveStringRedisTemplate redis,
                           @Value("${documind.ratelimit.per-minute:10}") int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/ask")) {
            return chain.filter(exchange);
        }
        String user = (String) exchange.getAttributes().getOrDefault(AuthFilter.USER_ATTR, "anonymous");
        long window = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:%s:%d".formatted(user, window);

        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> ttl = (count != null && count == 1L)
                            ? redis.expire(key, Duration.ofSeconds(60))
                            : Mono.just(true);
                    return ttl.then(Mono.defer(() -> {
                        if (count != null && count > limitPerMinute) {
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            return exchange.getResponse().setComplete();
                        }
                        return chain.filter(exchange);
                    }));
                })
                .onErrorResume(e -> chain.filter(exchange));   // Redis down -> don't block requests
    }

    @Override
    public int getOrder() {
        return -50;   // after auth (so the user attribute is set), before routing
    }
}

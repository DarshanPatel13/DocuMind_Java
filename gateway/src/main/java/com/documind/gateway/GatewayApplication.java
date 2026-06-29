package com.documind.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The gateway: the only service exposed to the browser. Owns the cross-cutting
 * concerns (JWT auth, CORS, Redis rate limiting, correlation ids) and reverse-
 * proxies to document-service / query-service. Reactive (Netty), so the
 * query-service SSE stream passes straight through token-by-token.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

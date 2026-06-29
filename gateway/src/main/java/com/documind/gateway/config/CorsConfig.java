package com.documind.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS for the WHOLE gateway — both the local controllers ({@code /auth/login},
 * {@code /health}) and the proxied routes. Spring Cloud Gateway's {@code globalcors}
 * only covers routed requests, so a browser preflight to {@code /auth/login} was
 * 403'd; this {@link CorsWebFilter} runs first and applies to every path.
 */
@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter(
            @Value("${documind.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}

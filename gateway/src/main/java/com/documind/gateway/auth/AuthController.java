package com.documind.gateway.auth;

import com.documind.contracts.LoginRequest;
import com.documind.contracts.TokenResponse;
import com.documind.gateway.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Login endpoint. Validates against the configured demo user (bcrypt-checked) and
 * issues a JWT. A single configured credential stands in for the Python users
 * table, which only ever seeds the demo user.
 */
@RestController
public class AuthController {

    private final JwtService jwtService;
    private final String demoUsername;
    private final String demoPasswordHash;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JwtService jwtService,
                          @Value("${documind.auth.demo-username:demo}") String demoUsername,
                          @Value("${documind.auth.demo-password:demo12345}") String demoPassword) {
        this.jwtService = jwtService;
        this.demoUsername = demoUsername;
        this.demoPasswordHash = encoder.encode(demoPassword);   // hash once at startup
    }

    @PostMapping("/auth/login")
    public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest request) {
        boolean ok = demoUsername.equals(request.username())
                && encoder.matches(request.password(), demoPasswordHash);
        if (ok) {
            return Mono.just(ResponseEntity.ok(new TokenResponse(jwtService.issue(request.username()), "bearer")));
        }
        return Mono.just(ResponseEntity.status(401).build());
    }
}

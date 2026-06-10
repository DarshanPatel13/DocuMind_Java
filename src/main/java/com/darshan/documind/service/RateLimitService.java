package com.darshan.documind.service;

import com.darshan.documind.config.DocuMindProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window in-memory rate limiter: N requests per client key per minute.
 *
 * <p>In-memory means per-instance, which is fine for a single node. With
 * multiple instances the counters would move to Redis (or a gateway limiter)
 * so all nodes share one budget. Map growth is bounded in practice by the
 * number of distinct client IPs seen per minute — each key's window record is
 * replaced, not accumulated, as minutes roll over.</p>
 */
@Service
public class RateLimitService {

    private record Window(long epochMinute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitService(DocuMindProperties properties) {
        this.requestsPerMinute = properties.rateLimit().requestsPerMinute();
    }

    /** @return true if this request fits in the caller's budget for the current minute. */
    public boolean tryAcquire(String clientKey) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        // compute() is atomic per key: rolls the window over when the minute
        // changes, otherwise keeps the existing counter.
        Window window = windows.compute(clientKey, (key, existing) ->
                existing == null || existing.epochMinute() != currentMinute
                        ? new Window(currentMinute, new AtomicInteger())
                        : existing);
        return window.count().incrementAndGet() <= requestsPerMinute;
    }
}

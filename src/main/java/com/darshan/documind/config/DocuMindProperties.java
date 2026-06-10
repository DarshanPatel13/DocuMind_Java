package com.darshan.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the {@code documind.*} block in application.yml.
 * Records give immutable, constructor-bound configuration with zero boilerplate.
 */
@ConfigurationProperties(prefix = "documind")
public record DocuMindProperties(
        String storageDir,
        Chunking chunking,
        Kafka kafka,
        Ask ask,
        RateLimit rateLimit) {

    /**
     * Chunking knobs. Token counts are converted to characters using the cheap
     * heuristic {@code 1 token ~ charsPerToken characters} — see TextChunker
     * for why an approximation is good enough here.
     */
    public record Chunking(int chunkSizeTokens, int overlapTokens, int charsPerToken) {
    }

    public record Kafka(String documentEventsTopic, String documentEventsDltTopic) {
    }

    /** topK = how many chunks are retrieved as context for each question. */
    public record Ask(int topK) {
    }

    public record RateLimit(int requestsPerMinute) {
    }
}

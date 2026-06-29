package com.documind.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * The Kafka payload published by document-service after an upload and consumed
 * by the ingestion worker. Keyed by {@code documentId} so all events for one
 * document keep partition order. Lives in the shared contract so producer and
 * consumer can never disagree on its shape.
 */
public record DocumentUploadedEvent(
        UUID documentId,
        String filename,
        String storagePath,
        Instant uploadedAt
) {
}

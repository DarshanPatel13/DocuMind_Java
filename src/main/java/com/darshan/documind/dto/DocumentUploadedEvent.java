package com.darshan.documind.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published to {@code document-events} after a successful upload
 * and consumed by the ingestion pipeline. It carries everything the consumer
 * needs (including the absolute storage path) so ingestion never has to call
 * back into the web layer.
 */
public record DocumentUploadedEvent(UUID documentId, String filename, String storagePath, Instant uploadedAt) {
}

package com.documind.contracts;

import java.util.UUID;

/**
 * The 202-Accepted body returned immediately after an upload, before the async
 * Kafka ingestion runs. Serialized as {@code {document_id, status, message}}.
 */
public record UploadResponse(
        UUID documentId,
        String status,
        String message
) {
}

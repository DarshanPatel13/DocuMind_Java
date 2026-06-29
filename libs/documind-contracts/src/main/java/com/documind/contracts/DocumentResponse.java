package com.documind.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata returned by {@code GET /api/documents}. {@code chunkCount}
 * and {@code failureReason} surface ingestion progress/outcome. Serialized as
 * {@code uploaded_at}, {@code chunk_count}, {@code failure_reason}.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String status,
        Instant uploadedAt,
        int chunkCount,
        String failureReason
) {
}

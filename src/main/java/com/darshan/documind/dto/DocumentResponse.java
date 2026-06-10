package com.darshan.documind.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** One row of GET /api/documents. failureReason only appears for FAILED documents. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        UUID id,
        String filename,
        String status,
        Instant uploadedAt,
        int chunkCount,
        String failureReason) {
}

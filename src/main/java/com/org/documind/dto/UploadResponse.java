package com.darshan.documind.dto;

import java.util.UUID;

/** Returned with 202 Accepted: the upload is durable, ingestion continues asynchronously. */
public record UploadResponse(UUID documentId, String status, String message) {
}

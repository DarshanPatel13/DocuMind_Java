package com.org.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of POST /api/ask. {@code documentId} (optional) narrows retrieval to a
 * single document; {@code conversationId} (optional) groups turns into one
 * conversation — a fresh id is generated when absent.
 */
public record AskRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question,
        UUID documentId,
        String conversationId) {
}

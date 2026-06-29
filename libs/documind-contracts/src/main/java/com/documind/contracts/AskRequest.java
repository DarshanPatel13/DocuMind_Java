package com.documind.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A question for the RAG ask flow. {@code documentId} optionally scopes
 * retrieval to one document; {@code conversationId} continues an existing thread
 * (a new one is minted when null). Serialized as snake_case
 * ({@code document_id}, {@code conversation_id}) to match the React frontend.
 */
public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        UUID documentId,
        String conversationId
) {
}

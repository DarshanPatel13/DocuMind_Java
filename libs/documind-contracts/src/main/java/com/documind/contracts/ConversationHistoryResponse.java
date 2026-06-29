package com.documind.contracts;

import java.util.List;

/** Full history for one conversation: {@code GET /api/conversations/{id}}. */
public record ConversationHistoryResponse(
        String conversationId,
        List<ConversationTurnResponse> turns
) {
}

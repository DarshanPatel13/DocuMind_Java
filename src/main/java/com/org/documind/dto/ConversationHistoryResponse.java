package com.org.documind.dto;

import java.util.List;

public record ConversationHistoryResponse(String conversationId, List<ConversationTurnResponse> turns) {
}

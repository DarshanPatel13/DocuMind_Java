package com.darshan.documind.dto;

import java.time.Instant;
import java.util.List;

public record ConversationTurnResponse(
        String question,
        String answer,
        List<Citation> citations,
        Instant timestamp) {
}

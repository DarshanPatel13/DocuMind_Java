package com.documind.contracts;

import java.time.Instant;
import java.util.List;

/** One question/answer turn with its citations, for the history endpoint. */
public record ConversationTurnResponse(
        String question,
        String answer,
        List<Citation> citations,
        Instant timestamp
) {
}

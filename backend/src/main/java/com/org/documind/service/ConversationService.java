package com.org.documind.service;

import com.org.documind.dto.ConversationHistoryResponse;
import com.org.documind.dto.ConversationTurnResponse;
import com.org.documind.entity.ConversationTurn;
import com.org.documind.exception.ConversationNotFoundException;
import com.org.documind.repository.ConversationTurnRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Reads Q&A history back from MongoDB for GET /api/conversations/{id}. */
@Service
public class ConversationService {

    private final ConversationTurnRepository repository;

    public ConversationService(ConversationTurnRepository repository) {
        this.repository = repository;
    }

    public ConversationHistoryResponse history(String conversationId) {
        List<ConversationTurn> turns = repository.findByConversationIdOrderByTimestampAsc(conversationId);
        if (turns.isEmpty()) {
            throw new ConversationNotFoundException(conversationId);
        }
        List<ConversationTurnResponse> mapped = turns.stream()
                .map(t -> new ConversationTurnResponse(
                        t.getQuestion(), t.getAnswer(), t.getCitations(), t.getTimestamp()))
                .toList();
        return new ConversationHistoryResponse(conversationId, mapped);
    }
}

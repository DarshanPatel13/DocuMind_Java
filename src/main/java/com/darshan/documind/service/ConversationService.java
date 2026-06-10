package com.darshan.documind.service;

import com.darshan.documind.dto.ConversationHistoryResponse;
import com.darshan.documind.dto.ConversationTurnResponse;
import com.darshan.documind.entity.ConversationTurn;
import com.darshan.documind.exception.ConversationNotFoundException;
import com.darshan.documind.repository.ConversationTurnRepository;
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

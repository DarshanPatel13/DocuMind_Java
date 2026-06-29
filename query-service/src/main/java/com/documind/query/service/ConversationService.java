package com.documind.query.service;

import com.documind.contracts.Citation;
import com.documind.contracts.ConversationHistoryResponse;
import com.documind.contracts.ConversationTurnResponse;
import com.documind.query.exception.ConversationNotFoundException;
import com.documind.query.mongo.ConversationTurnDocument;
import com.documind.query.mongo.ConversationTurnRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Persists and reads conversation turns (MongoDB). */
@Service
public class ConversationService {

    private final ConversationTurnRepository repository;

    public ConversationService(ConversationTurnRepository repository) {
        this.repository = repository;
    }

    public void saveTurn(String conversationId, String question, String answer,
                         List<Citation> citations, List<String> retrievedChunkIds) {
        repository.save(new ConversationTurnDocument(
                conversationId, question, answer, citations, retrievedChunkIds, Instant.now()));
    }

    public ConversationHistoryResponse getHistory(String conversationId) {
        List<ConversationTurnDocument> turns = repository.findByConversationIdOrderByTimestampAsc(conversationId);
        if (turns.isEmpty()) {
            throw new ConversationNotFoundException(conversationId);
        }
        List<ConversationTurnResponse> mapped = turns.stream()
                .map(t -> new ConversationTurnResponse(t.getQuestion(), t.getAnswer(), t.getCitations(), t.getTimestamp()))
                .toList();
        return new ConversationHistoryResponse(conversationId, mapped);
    }
}

package com.documind.query.mongo;

import com.documind.contracts.Citation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One persisted question/answer turn. Conversation history is an append-only,
 * schema-flexible log always read back whole by {@code conversationId} — a
 * document store's sweet spot.
 */
@Document(collection = "conversation_turns")
public class ConversationTurnDocument {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private String question;
    private String answer;
    private List<Citation> citations;
    private List<String> retrievedChunkIds;
    private Instant timestamp;

    public ConversationTurnDocument() {
    }

    public ConversationTurnDocument(String conversationId, String question, String answer,
                                    List<Citation> citations, List<String> retrievedChunkIds, Instant timestamp) {
        this.conversationId = conversationId;
        this.question = question;
        this.answer = answer;
        this.citations = citations;
        this.retrievedChunkIds = retrievedChunkIds;
        this.timestamp = timestamp;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public List<String> getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

package com.darshan.documind.entity;

import com.darshan.documind.dto.Citation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One question/answer exchange, persisted in MongoDB.
 *
 * <p>Mongo rather than Postgres because conversation history is an
 * append-only, schema-flexible log that is always read back whole by
 * conversationId — a document store's sweet spot. The {@link Citation} value
 * object is embedded as-is so the stored shape matches the API shape.</p>
 */
@Document(collection = "conversation_turns")
public class ConversationTurn {

    @Id
    private String id;

    /** History lookups are always by conversationId, hence the index. */
    @Indexed
    private String conversationId;

    private String question;
    private String answer;
    private List<Citation> citations;
    private List<String> retrievedChunkIds;
    private Instant timestamp;

    /** For the Spring Data Mongo mapper. */
    protected ConversationTurn() {
    }

    public ConversationTurn(String conversationId, String question, String answer,
                            List<Citation> citations, List<String> retrievedChunkIds,
                            Instant timestamp) {
        this.conversationId = conversationId;
        this.question = question;
        this.answer = answer;
        this.citations = citations;
        this.retrievedChunkIds = retrievedChunkIds;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
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

package com.documind.query.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** History lookups are always by conversationId (indexed), in chronological order. */
public interface ConversationTurnRepository extends MongoRepository<ConversationTurnDocument, String> {

    List<ConversationTurnDocument> findByConversationIdOrderByTimestampAsc(String conversationId);
}

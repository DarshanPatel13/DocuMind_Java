package com.org.documind.repository;

import com.org.documind.entity.ConversationTurn;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConversationTurnRepository extends MongoRepository<ConversationTurn, String> {

    /** A conversation's full history, oldest turn first. */
    List<ConversationTurn> findByConversationIdOrderByTimestampAsc(String conversationId);
}

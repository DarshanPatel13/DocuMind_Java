package com.documind.query.exception;

/** No conversation exists for the requested id. -> 404. */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(String conversationId) {
        super("Conversation not found: " + conversationId);
    }
}

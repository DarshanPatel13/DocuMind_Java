package com.documind.document.exception;

import java.util.UUID;

/** The referenced document row does not exist. Not retryable on the consumer. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id);
    }
}

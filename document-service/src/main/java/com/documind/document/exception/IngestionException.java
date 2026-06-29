package com.documind.document.exception;

/** A recoverable ingestion failure (e.g. no extractable text) — retried, then DLT'd. */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }
}

package com.documind.document.exception;

/** Thrown when an upload is not a valid PDF / is empty / is too large. -> 400. */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}

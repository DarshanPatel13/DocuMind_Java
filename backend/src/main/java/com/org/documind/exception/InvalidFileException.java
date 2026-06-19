package com.org.documind.exception;

/** Upload validation failure: not a PDF, empty, or over the size limit. Maps to 400. */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}

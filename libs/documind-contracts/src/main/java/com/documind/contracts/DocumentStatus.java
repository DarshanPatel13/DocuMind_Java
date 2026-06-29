package com.documind.contracts;

/**
 * Lifecycle of an uploaded document. Mirrors the Python {@code DocumentStatus}
 * enum: UPLOADED &rarr; PROCESSING &rarr; READY (or FAILED). Stored as a plain
 * string in the {@code documents} table to keep migrations trivial.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}

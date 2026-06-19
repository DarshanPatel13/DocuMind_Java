package com.org.documind.dto;

/**
 * A pointer to the exact chunk an answer was grounded on — rendered in answers
 * as {@code [filename, chunk N]}. Shared by the API response and the MongoDB
 * conversation log so both always agree on the shape.
 */
public record Citation(String filename, int chunkIndex) {
}

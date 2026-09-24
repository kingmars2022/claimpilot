package com.companybrain.chat;

/**
 * A source passage the answer relies on. {@code index} matches the [n] markers in the answer.
 */
public record Citation(
        int index,
        String documentId,
        String fileName,
        Integer page,
        String section,
        String snippet,
        Double score) {
}

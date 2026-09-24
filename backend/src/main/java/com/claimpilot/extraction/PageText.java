package com.claimpilot.extraction;

/**
 * Text of one page of a document.
 *
 * @param page 1-based page number, or null for formats without pages (Markdown, plain text)
 */
public record PageText(Integer page, String text) {
}

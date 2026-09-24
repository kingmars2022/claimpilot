package com.claimpilot.extraction;

/**
 * A fact read from a document.
 *
 * @param value    normalized value (ISO date, plain decimal amount, or text)
 * @param quote    the document text that supports the value
 * @param page     page where the quote was found, or null
 * @param verified true when code found the quote in the document and the value inside the quote;
 *                 false means the model's answer could not be confirmed and the user must check it
 */
public record ExtractedFact(FactKey key, String value, String quote, Integer page, boolean verified) {
}

package com.claimpilot.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "claimpilot" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "claimpilot")
public record AppProperties(Storage storage, Indexing indexing, Retrieval retrieval, Security security,
                            Conversation conversation, Ocr ocr) {

    public record Storage(String localRoot) {
    }

    /**
     * @param chunkSize maximum chunk length in tokens; smaller chunks give more precise citations
     */
    public record Indexing(int chunkSize) {
    }

    /**
     * @param topK                how many chunks to retrieve per question
     * @param similarityThreshold minimum cosine similarity (0-1) for a chunk to count as relevant
     */
    public record Retrieval(int topK, double similarityThreshold) {
    }

    /**
     * @param jwtSecret signing key for access tokens (HS256); at least 32 characters
     * @param tokenTtl  how long an access token stays valid
     */
    public record Security(String jwtSecret, Duration tokenTtl) {
    }

    /**
     * @param historyTurns how many earlier question/answer pairs are given to the model as context for a follow-up
     */
    public record Conversation(int historyTurns) {
    }

    /**
     * @param command   the Tesseract executable
     * @param languages Tesseract language codes, for example "eng" or "eng+fra"
     */
    public record Ocr(String command, String languages) {
    }
}

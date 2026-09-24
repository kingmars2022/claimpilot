package com.companybrain.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "companybrain" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "companybrain")
public record AppProperties(Storage storage, Indexing indexing, Retrieval retrieval, Security security,
                            Conversation conversation) {

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
     * @param historyTurns how many earlier question/answer pairs are used to rewrite a follow-up question
     */
    public record Conversation(int historyTurns) {
    }
}

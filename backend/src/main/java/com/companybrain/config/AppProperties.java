package com.companybrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "companybrain" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "companybrain")
public record AppProperties(Storage storage, Indexing indexing, Retrieval retrieval) {

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
}

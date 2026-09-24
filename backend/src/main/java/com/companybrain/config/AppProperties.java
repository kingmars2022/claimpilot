package com.companybrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "companybrain" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "companybrain")
public record AppProperties(Storage storage, Retrieval retrieval) {

    public record Storage(String localRoot) {
    }

    /**
     * @param topK                how many chunks to retrieve per question
     * @param similarityThreshold minimum cosine similarity (0-1) for a chunk to count as relevant
     */
    public record Retrieval(int topK, double similarityThreshold) {
    }
}

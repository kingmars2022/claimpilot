package com.claimpilot.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "claimpilot" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "claimpilot")
public record AppProperties(Storage storage, Indexing indexing, Retrieval retrieval, Security security,
                            Conversation conversation, Ocr ocr, Events events) {

    /**
     * @param type          "local" (a folder on disk) or "s3" (Amazon S3, or RustFS locally)
     * @param localRoot     folder for uploaded files when they are kept on the local disk
     * @param encryptionKey base64 AES-256 key; files are encrypted at rest when it is set
     */
    public record Storage(String type, String localRoot, String encryptionKey, S3 s3) {
    }

    /**
     * @param endpoint  empty for Amazon S3; the RustFS URL locally, for example http://localhost:9000
     * @param pathStyle true for RustFS (bucket in the path rather than the host name)
     * @param accessKey empty to use the default AWS credentials chain (environment, instance role)
     */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
                     boolean pathStyle) {
    }

    /**
     * @param mode           "inline": uploads are processed on a background thread of this server;
     *                       "kafka": an event is published and a worker consumes it
     * @param uploadedTopic  events for new uploads, consumed by the processing workers
     * @param processedTopic events for finished processing, relayed to the users' browsers
     */
    public record Events(String mode, String uploadedTopic, String processedTopic) {

        public boolean kafka() {
            return "kafka".equalsIgnoreCase(mode);
        }
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

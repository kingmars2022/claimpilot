package com.claimpilot.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the "claimpilot" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "claimpilot")
public record AppProperties(Storage storage, Indexing indexing, Retrieval retrieval, Security security,
                            Conversation conversation, Ocr ocr, Events events, Cache cache,
                            Processing processing, Audit audit) {

    /**
     * @param type          "local" (a folder on disk) or "s3" (Amazon S3, or RustFS locally)
     * @param localRoot     folder for uploaded files when they are kept on the local disk
     * @param encryptionKey base64 AES-256 key; files are encrypted at rest when it is set
     * @param maxFilesPerUser policies, receipts and forms one user may keep
     * @param maxBytesPerUser total size of one user's files
     */
    public record Storage(String type, String localRoot, String encryptionKey, S3 s3, int maxFilesPerUser,
                          long maxBytesPerUser) {
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
     * @param maxConcurrent documents processed at the same time per server (OCR and model calls are heavy)
     */
    public record Processing(int maxConcurrent) {
    }

    /**
     * @param retention how long activity log entries are kept
     */
    public record Audit(Duration retention) {
    }

    /**
     * @param type                   "memory" (one server) or "redis" (shared by every server)
     * @param ttl                    how long a model reply is reused for the same prompt
     * @param modelRequestsPerMinute per user: questions, assistant messages, guides, claims and uploads
     * @param loginAttemptsPerMinute per client address and per username, for sign-in and sign-up
     */
    public record Cache(String type, Duration ttl, int modelRequestsPerMinute, int loginAttemptsPerMinute) {

        public boolean redis() {
            return "redis".equalsIgnoreCase(type);
        }
    }

    /**
     * @param mode           "inline": uploads are processed on a background thread of this server;
     *                       "kafka": an event is published and a worker consumes it;
     *                       "sqs": S3 notifies an SQS queue of each stored file and a worker consumes it
     * @param uploadedTopic  events for new uploads, consumed by the processing workers
     * @param processedTopic events for finished processing, relayed to the users' browsers
     */
    public record Events(String mode, String uploadedTopic, String processedTopic, Sqs sqs) {

        public boolean kafka() {
            return "kafka".equalsIgnoreCase(mode);
        }
    }

    /**
     * @param queueUrl        the queue that receives the bucket's "object created" notifications
     * @param endpoint        empty for Amazon SQS; ElasticMQ locally, for example http://localhost:9324
     * @param publishUploads  true where the storage cannot send notifications itself (locally): the
     *                        app posts the same S3-style event after storing a file. False on AWS,
     *                        where S3 sends it.
     */
    public record Sqs(String queueUrl, String endpoint, String region, String accessKey, String secretKey,
                      boolean publishUploads, int visibilityTimeoutSeconds) {
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
     * @param allowDevSecrets false outside local development: the public development keys are refused
     */
    public record Security(String jwtSecret, Duration tokenTtl, boolean allowDevSecrets) {
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

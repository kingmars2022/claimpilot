package com.companybrain.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String fileName,
        long sizeBytes,
        DocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant indexedAt) {

    static DocumentResponse from(KnowledgeDocument doc) {
        return new DocumentResponse(doc.getId(), doc.getFileName(), doc.getSizeBytes(), doc.getStatus(),
                doc.getChunkCount(), doc.getErrorMessage(), doc.getCreatedAt(), doc.getIndexedAt());
    }
}

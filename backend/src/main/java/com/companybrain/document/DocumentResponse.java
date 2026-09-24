package com.companybrain.document;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.companybrain.user.DepartmentResponse;

/** @param visibleTo departments that may see the document; empty means the whole company */
public record DocumentResponse(
        UUID id,
        String fileName,
        long sizeBytes,
        DocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant indexedAt,
        List<DepartmentResponse> visibleTo) {

    static DocumentResponse from(KnowledgeDocument doc) {
        return new DocumentResponse(doc.getId(), doc.getFileName(), doc.getSizeBytes(), doc.getStatus(),
                doc.getChunkCount(), doc.getErrorMessage(), doc.getCreatedAt(), doc.getIndexedAt(),
                doc.getDepartments().stream()
                        .map(DepartmentResponse::from)
                        .sorted(Comparator.comparing(DepartmentResponse::name))
                        .toList());
    }
}

package com.claimpilot.document;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.claimpilot.extraction.DocumentFact;

/**
 * @param facts key facts read from the document, each with its quote, page and whether code could
 *              verify it against the document text
 */
public record DocumentResponse(
        UUID id,
        DocumentKind kind,
        String fileName,
        long sizeBytes,
        DocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant processedAt,
        List<Fact> facts) {

    public record Fact(String key, String value, String quote, Integer page, boolean verified) {
    }

    static DocumentResponse from(UploadedDocument doc, List<DocumentFact> facts) {
        return new DocumentResponse(doc.getId(), doc.getKind(), doc.getFileName(), doc.getSizeBytes(), doc.getStatus(),
                doc.getChunkCount(), doc.getErrorMessage(), doc.getCreatedAt(), doc.getProcessedAt(),
                facts.stream()
                        .sorted(Comparator.comparing(f -> f.getKey().ordinal()))
                        .map(f -> new Fact(f.getKey().name(), f.getValue(), f.getQuote(), f.getPage(), f.isVerified()))
                        .toList());
    }
}

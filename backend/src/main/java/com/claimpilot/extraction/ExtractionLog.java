package com.claimpilot.extraction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The raw model reply behind each extraction, kept in MongoDB because its shape differs by
 * document and model. Useful to debug a wrong value and, later, to measure accuracy.
 */
@Document("extraction_logs")
public record ExtractionLog(
        @Id String id,
        @Indexed UUID documentId,
        @Indexed Long ownerId,
        String documentKind,
        String model,
        String rawReply,
        List<ExtractedFact> facts,
        long durationMs,
        Instant createdAt) {
}

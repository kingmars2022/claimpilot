package com.claimpilot.events;

import java.time.Instant;
import java.util.UUID;

import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentStatus;

/** Published when processing of an upload ends, successfully or not. */
public record DocumentProcessed(UUID documentId, Long ownerId, DocumentKind kind, String fileName,
                                DocumentStatus status, String errorMessage, Instant at) {
}

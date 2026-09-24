package com.claimpilot.document;

import java.util.UUID;

/** Published after a document is deleted, so in-memory copies of it can be dropped too. */
public record DocumentDeleted(UUID documentId, Long ownerId, DocumentKind kind) {
}

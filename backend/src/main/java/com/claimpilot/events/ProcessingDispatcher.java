package com.claimpilot.events;

import java.util.UUID;

/**
 * Hands a new upload over for processing. Inline mode runs it on a background thread of this
 * server; Kafka mode publishes an event that any worker instance can consume, so uploads survive a
 * restart and processing scales separately from the API.
 */
public interface ProcessingDispatcher {

    void documentUploaded(UUID documentId);
}

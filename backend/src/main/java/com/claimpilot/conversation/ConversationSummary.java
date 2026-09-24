package com.claimpilot.conversation;

import java.time.Instant;
import java.util.UUID;

/** A conversation in the history list, without its messages. */
public record ConversationSummary(String id, UUID policyId, String title, Instant updatedAt) {
}

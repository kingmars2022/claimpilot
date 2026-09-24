package com.companybrain.conversation;

import java.time.Instant;

/** A conversation in the history list, without its messages. */
public record ConversationSummary(String id, String title, Instant updatedAt) {
}

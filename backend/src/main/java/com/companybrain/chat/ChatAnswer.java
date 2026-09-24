package com.companybrain.chat;

import java.util.List;

/**
 * @param grounded       true when the answer cites at least one source from the knowledge base
 * @param conversationId the conversation this answer was saved to
 * @param searchQuery    the standalone question that was searched, when a follow-up was rewritten;
 *                       null when the question was searched as asked
 */
public record ChatAnswer(
        String answer,
        boolean grounded,
        List<Citation> citations,
        long latencyMs,
        String conversationId,
        String searchQuery) {
}

package com.claimpilot.chat;

import java.util.List;
import java.util.UUID;

/**
 * @param status         whether the policy answers the question, is unclear, or is silent
 * @param language       language code of the answer: en, fr or zh
 * @param citations      clauses the answer relies on; for UNCLEAR, the clauses to read
 * @param callKit        how to ask the insurer; set when the answer is UNCLEAR or NOT_IN_POLICY
 * @param conversationId the conversation this answer was saved to
 */
public record ChatAnswer(
        String answer,
        AnswerStatus status,
        String language,
        List<Citation> citations,
        CallKit callKit,
        long latencyMs,
        String conversationId,
        UUID policyId) {
}

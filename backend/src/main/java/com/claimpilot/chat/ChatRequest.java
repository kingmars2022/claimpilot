package com.claimpilot.chat;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param question       the question, in English, French or Chinese
 * @param policyId       the policy to ask about
 * @param conversationId the conversation to continue; null starts a new one
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotNull UUID policyId,
        @Size(max = 64) String conversationId) {
}

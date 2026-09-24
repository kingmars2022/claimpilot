package com.companybrain.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question       the employee's question
 * @param conversationId the conversation to continue; null starts a new one
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 64) String conversationId) {
}

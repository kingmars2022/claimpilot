package com.companybrain.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question the employee's question
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String question) {
}

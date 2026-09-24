package com.companybrain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void numbersSourcesAndIncludesPages() {
        List<Document> sources = List.of(
                new Document("Employees get 15 vacation days.", Map.of("fileName", "handbook.pdf", "page", 4)),
                new Document("Client meals are reimbursed.", Map.of("fileName", "expenses.md")));

        String prompt = builder.userPrompt("How many days?", sources);

        assertThat(prompt)
                .contains("[1] (file: handbook.pdf, page 4)")
                .contains("[2] (file: expenses.md)")
                .contains("Question: How many days?")
                .endsWith(PromptBuilder.ANSWER_LANGUAGE_REMINDER);
    }

    @Test
    void systemPromptContainsFixedNoAnswerText() {
        assertThat(builder.systemPrompt())
                .contains("Use ONLY facts from the numbered sources")
                .contains(PromptBuilder.NO_ANSWER);
    }

    @Test
    void extractsCitationsInOrderWithoutDuplicates() {
        assertThat(PromptBuilder.citedIndices("Take 15 days [2]. Ask your manager [1][2]."))
                .containsExactly(2, 1);
        assertThat(PromptBuilder.citedIndices("No sources here.")).isEmpty();
    }

    @Test
    void removesReasoningBlocks() {
        assertThat(PromptBuilder.clean("<think>internal</think>\nAnswer [1]")).isEqualTo("Answer [1]");
    }
}

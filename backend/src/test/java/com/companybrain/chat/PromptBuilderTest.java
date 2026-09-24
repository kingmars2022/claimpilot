package com.companybrain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.companybrain.conversation.ChatMessage;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void numbersSourcesAndIncludesPages() {
        List<Document> sources = List.of(
                new Document("Employees get 15 vacation days.", Map.of("fileName", "handbook.pdf", "page", 4)),
                new Document("Client meals are reimbursed.", Map.of("fileName", "expenses.md")));

        String prompt = builder.userPrompt("How many days?", sources, List.of());

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

    @Test
    void followUpPromptIncludesEarlierTurnsWithoutOldCitationMarkers() {
        List<Document> sources = List.of(new Document("From the third year: 20 days.", Map.of("fileName", "h.md")));
        String prompt = builder.userPrompt("And year three?", sources, List.of(
                ChatMessage.question("How many vacation days in year one?"),
                ChatMessage.answer("You get 15 days [1].", true, List.of())));

        assertThat(prompt)
                .startsWith("Earlier in this conversation:")
                .contains("Employee: How many vacation days in year one?")
                .contains("Assistant: You get 15 days .")
                .contains("[1] (file: h.md)")
                .contains("Question: And year three?");
    }

    @Test
    void contextualQueryAddsThePreviousQuestion() {
        List<ChatMessage> history = List.of(
                ChatMessage.question("How many vacation days in year one?"),
                ChatMessage.answer("15 days [1].", true, List.of()));

        assertThat(PromptBuilder.contextualQuery(history, "And year three?"))
                .isEqualTo("How many vacation days in year one?\nAnd year three?");
        assertThat(PromptBuilder.contextualQuery(List.of(), "Hello?")).isEqualTo("Hello?");
    }
}

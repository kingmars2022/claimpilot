package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.claimpilot.conversation.ChatMessage;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void readsTheStatusLineAndRemovesIt() {
        PromptBuilder.ParsedReply reply = PromptBuilder.parse("STATUS: ANSWERED\nPhysiotherapy is reimbursed at 80% [1].");
        assertThat(reply.status()).contains(AnswerStatus.ANSWERED);
        assertThat(reply.text()).isEqualTo("Physiotherapy is reimbursed at 80% [1].");

        assertThat(PromptBuilder.parse("<think>x</think>**STATUS: NOT_IN_POLICY**").status())
                .contains(AnswerStatus.NOT_IN_POLICY);
        assertThat(PromptBuilder.parse("Just an answer [1].").status()).isEmpty();
    }

    @Test
    void promptCitesPagesAndAsksForTheQuestionLanguage() {
        List<Document> sources = List.of(new Document("Physiotherapist: up to $600 per year.",
                Map.of("fileName", "policy.pdf", "page", 2)));

        String prompt = builder.userPrompt("La physio est-elle couverte ?", sources, List.of(), AnswerLanguage.FRENCH);

        assertThat(prompt).contains("[1] (page 2)").contains("Question: La physio est-elle couverte ?")
                .endsWith("answer in French with [n] citations.");
        assertThat(builder.systemPrompt(AnswerLanguage.FRENCH)).contains("Write in French").contains("STATUS: UNCLEAR");
    }

    @Test
    void followUpPromptIncludesEarlierTurnsWithoutOldMarkers() {
        String prompt = builder.userPrompt("And massage?", List.of(new Document("Massage: $400.", Map.of("page", 2))),
                List.of(ChatMessage.question("Is physio covered?"),
                        ChatMessage.answer("Yes, 80% [1].", AnswerStatus.ANSWERED, List.of(), null)),
                AnswerLanguage.ENGLISH);

        assertThat(prompt).startsWith("Earlier in this conversation:")
                .contains("Member: Is physio covered?")
                .contains("Assistant: Yes, 80% .");
        assertThat(PromptBuilder.contextualQuery(List.of(ChatMessage.question("Is physio covered?")), "And massage?"))
                .isEqualTo("Is physio covered?\nAnd massage?");
    }
}

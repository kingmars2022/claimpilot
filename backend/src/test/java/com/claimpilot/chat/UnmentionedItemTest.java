package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class UnmentionedItemTest {

    private static final List<Document> PARAMEDICAL = List.of(
            doc("Physiotherapist: up to $600 per calendar year per person."),
            doc("Practitioners must be members in good standing. Treatments by a kinesiologist may be considered "
                    + "when they are part of a rehabilitation program."));

    @Test
    void anItemNoClauseNamesIsNotInThePolicy() {
        assertThat(UnmentionedItem.applies("Is acupuncture covered?", AnswerLanguage.ENGLISH, PARAMEDICAL)).isTrue();
        assertThat(UnmentionedItem.item("Are acupuncture treatments for my son covered?", AnswerLanguage.ENGLISH,
                PARAMEDICAL)).contains("acupuncture");
        assertThat(UnmentionedItem.answer("acupuncture")).contains("\"acupuncture\"").contains("another name");
    }

    @Test
    void anItemAClauseNamesStaysUnclear() {
        assertThat(UnmentionedItem.applies("Are kinesiologist treatments covered?", AnswerLanguage.ENGLISH,
                PARAMEDICAL)).isFalse();
        assertThat(UnmentionedItem.applies("Is a physiotherapy visit covered?", AnswerLanguage.ENGLISH,
                PARAMEDICAL)).as("physiotherapist").isFalse();
    }

    @Test
    void theRuleStaysOutOfCasesItCannotJudge() {
        List<Document> withExclusions = List.of(doc("The plan does not cover: cosmetic procedures."));
        assertThat(UnmentionedItem.applies("Is Botox for wrinkles covered?", AnswerLanguage.ENGLISH, withExclusions))
                .as("may be a cosmetic procedure").isFalse();
        assertThat(UnmentionedItem.applies("Est-ce que l'acupuncture est couverte ?", AnswerLanguage.FRENCH,
                PARAMEDICAL)).as("not English").isFalse();
        assertThat(UnmentionedItem.applies("How long do I have to submit acupuncture receipts?",
                AnswerLanguage.ENGLISH, PARAMEDICAL)).as("not a coverage question").isFalse();
    }

    private static Document doc(String text) {
        return Document.builder().text(text).build();
    }
}

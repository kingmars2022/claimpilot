package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerLanguageTest {

    @Test
    void detectsTheQuestionLanguage() {
        assertThat(AnswerLanguage.detect("Is physiotherapy covered?")).isEqualTo(AnswerLanguage.ENGLISH);
        assertThat(AnswerLanguage.detect("Est-ce que la physiothérapie est remboursée ?"))
                .isEqualTo(AnswerLanguage.FRENCH);
        assertThat(AnswerLanguage.detect("Combien pour la massothérapie ?")).isEqualTo(AnswerLanguage.FRENCH);
        assertThat(AnswerLanguage.detect("理疗能报销多少？")).isEqualTo(AnswerLanguage.CHINESE);
        assertThat(AnswerLanguage.detect("Is a café visit covered?")).isEqualTo(AnswerLanguage.ENGLISH);
    }
}

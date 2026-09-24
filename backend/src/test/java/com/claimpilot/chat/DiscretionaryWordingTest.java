package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiscretionaryWordingTest {

    private static final String SECTION_3 = "Physiotherapist: up to $600 per calendar year per person. Treatments by "
            + "a kinesiologist may be considered when they are part of a rehabilitation program.";

    @Test
    void anAnswerRestingOnAMayBeConsideredClauseIsNotAYes() {
        assertThat(DiscretionaryWording.decides(
                "Kinesiologist treatments may be considered when part of a rehabilitation program [1].",
                List.of(SECTION_3))).isTrue();
    }

    @Test
    void anotherSentenceOfTheSameClauseDoesNotChangeAClearAnswer() {
        assertThat(DiscretionaryWording.decides("Physiotherapy is covered up to $600 per year [1].",
                List.of(SECTION_3))).isFalse();
    }

    @Test
    void theModelsOwnCautionIsNotEnough() {
        assertThat(DiscretionaryWording.decides("It may be covered [1].",
                List.of("Chiropractor: up to $500 per calendar year per person."))).isFalse();
    }

    @Test
    void frenchAndChineseWordingIsRecognised() {
        assertThat(DiscretionaryWording.hedges("Les frais peuvent être considérés au cas par cas.")).isTrue();
        assertThat(DiscretionaryWording.hedges("保险公司会酌情决定")).isTrue();
        assertThat(DiscretionaryWording.hedges("You may submit the claim online.")).isFalse();
    }
}

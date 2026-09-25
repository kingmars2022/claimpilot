package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ThresholdRuleTest {

    private static final List<Document> CEDARVIEW = List.of(
            doc("Chiropractor: up to $500 per calendar year per person, including one X-ray per year."),
            doc("Basic services (exams, cleanings, fillings) are reimbursed at 80%. For any treatment plan over "
                    + "$500, submit a predetermination before treatment begins."));

    @Test
    void anAmountOverTheThresholdMeansTheRuleApplies() {
        Optional<ThresholdRule.Finding> finding = ThresholdRule.find(
                "Do I need a predetermination for a $900 dental treatment plan?", CEDARVIEW);

        assertThat(finding).isPresent();
        assertThat(finding.get().index()).as("the dental clause, not the chiropractor's $500").isEqualTo(2);
        assertThat(finding.get().threshold()).isEqualByComparingTo("500");
        assertThat(finding.get().applies()).isTrue();
        assertThat(finding.get().answer(AnswerLanguage.ENGLISH))
                .startsWith("Yes. $900 is over the $500 threshold")
                .contains("For any treatment plan over $500").endsWith("[2]");
    }

    @Test
    void anAmountUnderTheThresholdMeansItDoesNot() {
        ThresholdRule.Finding finding = ThresholdRule.find(
                "Do I need a predetermination for a $1,500.00 plan? No wait, $300", CEDARVIEW).orElse(null);
        assertThat(finding).as("two amounts: not settled by code").isNull();

        ThresholdRule.Finding under = ThresholdRule.find("Do I need a predetermination for 300 $ of treatment?",
                CEDARVIEW).orElseThrow();
        assertThat(under.amount()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(under.applies()).isFalse();
        assertThat(under.answer(AnswerLanguage.FRENCH)).startsWith("Non. 300 $ ne dépasse pas le seuil de 500 $");
    }

    @Test
    void aClauseOnAnotherSubjectOrAHedgedOneIsNotUsed() {
        assertThat(ThresholdRule.find("Is my $700 massage reimbursed?", CEDARVIEW)).isEmpty();
        assertThat(ThresholdRule.find("Do I need a predetermination for a $900 plan?",
                List.of(doc("A predetermination may be considered for a plan over $500.")))).isEmpty();
        assertThat(ThresholdRule.find("Do I need a predetermination?", CEDARVIEW)).as("no amount").isEmpty();
        List<Document> withMassage = List.of(
                doc("Massage therapist: up to $400 per calendar year per person. A physician's referral is required."),
                CEDARVIEW.get(1));
        assertThat(ThresholdRule.find("Do I need a predetermination for a $900 massage therapy plan?", withMassage))
                .as("the dental rule is not about massage").isEmpty();
    }

    private static Document doc(String text) {
        return Document.builder().text(text).build();
    }
}

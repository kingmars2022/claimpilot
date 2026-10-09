package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class QuoteCitationsTest {

    private static final List<Document> SOURCES = List.of(
            doc("Protection 3: All perils other than collision or upset - $250 deductible per loss.\n"
                    + "- No deductible applicable in the event of a windshield repair."),
            doc("Subdivision C - Reimbursement of medical expenses - $3,000 per person."),
            doc("- No deductible applicable in the event of entire theft\n(including a vehicle found damaged)."));

    @Test
    void aQuoteCitedToTheWrongExcerptPointsToTheOneThatHoldsIt() {
        String answer = "There is no deductible, as the policy states \"No deductible applicable in the event of "
                + "entire theft (including a vehicle found damaged)\" [1].";

        assertThat(QuoteCitations.fix(answer, SOURCES)).endsWith("(including a vehicle found damaged)\" [3].");
    }

    @Test
    void aCorrectCitationOrAnUnknownQuoteIsLeftAlone() {
        String right = "Repairs: “no deductible applicable in the event of a windshield repair” [1].";
        assertThat(QuoteCitations.fix(right, SOURCES)).isEqualTo(right);

        String oneOfTwo = "\"Reimbursement of medical expenses - $3,000 per person\" [1][2].";
        assertThat(QuoteCitations.fix(oneOfTwo, SOURCES)).as("one of the cited excerpts holds it").isEqualTo(oneOfTwo);

        String invented = "\"Rental cars are always covered in full\" [1].";
        assertThat(QuoteCitations.fix(invented, SOURCES)).as("not in any excerpt: nothing to point to")
                .isEqualTo(invented);

        String noQuote = "Medical expenses are reimbursed up to $3,000 [1].";
        assertThat(QuoteCitations.fix(noQuote, SOURCES)).isEqualTo(noQuote);
    }

    private static Document doc(String text) {
        return Document.builder().text(text).build();
    }
}

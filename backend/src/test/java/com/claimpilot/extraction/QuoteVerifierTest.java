package com.claimpilot.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class QuoteVerifierTest {

    private static final List<PageText> PAGES = List.of(
            new PageText(1, "Plan sponsor: Rive-Nord Logistics Ltd.\nGroup policy number: CV-88213-02"),
            new PageText(2, "Physiotherapist: up to $600 per calendar year per person."));

    @Test
    void exactQuoteIsVerifiedAndGivesThePage() {
        ExtractedFact fact = QuoteVerifier.verify(FactKey.POLICY_NUMBER, "CV-88213-02",
                "Group policy number: CV-88213-02", PAGES);

        assertThat(fact.verified()).isTrue();
        assertThat(fact.page()).isEqualTo(1);
    }

    @Test
    void paraphrasedQuoteFallsBackToTheLineHoldingTheValue() {
        ExtractedFact fact = QuoteVerifier.verify(FactKey.POLICY_NUMBER, "CV-88213-02",
                "The policy is number CV-88213-02", PAGES);

        assertThat(fact.verified()).isTrue();
        assertThat(fact.quote()).isEqualTo("Group policy number: CV-88213-02");
        assertThat(fact.page()).isEqualTo(1);
    }

    @Test
    void invented_valueIsNotVerified() {
        ExtractedFact fact = QuoteVerifier.verify(FactKey.CLAIMS_ADDRESS, "P.O. Box 1, Toronto",
                "Send claims to P.O. Box 1, Toronto", PAGES);

        assertThat(fact.verified()).isFalse();
        assertThat(fact.page()).isNull();
    }
}

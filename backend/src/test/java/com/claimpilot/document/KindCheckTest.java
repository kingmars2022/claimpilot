package com.claimpilot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.claimpilot.extraction.ExtractedFact;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.extraction.PageText;

class KindCheckTest {

    private static final List<PageText> RECEIPT = List.of(new PageText(1,
            "CLINIQUE PHYSIO PLATEAU\nRECEIPT / RECU\nDate of service: March 5, 2026\nTotal charged: $120.00"));
    private static final List<PageText> POLICY = List.of(new PageText(1,
            "Group policy number: CV-88213-02\nPlan member: Marc Gagnon\nCertificate number: 55190336"));

    @Test
    void aReceiptUploadedAsAPolicyIsFlagged() {
        // The model may still return a policy number; one that is not in the text does not count.
        List<ExtractedFact> invented = List.of(new ExtractedFact(FactKey.POLICY_NUMBER, "CV-1", "CV-1", null, false));

        assertThat(KindCheck.warning(DocumentKind.POLICY, RECEIPT, invented)).get().asString()
                .contains("looks like a receipt");
    }

    @Test
    void aPolicyUploadedAsAReceiptIsFlagged() {
        assertThat(KindCheck.warning(DocumentKind.RECEIPT, POLICY, List.of())).get().asString()
                .contains("looks like a policy");
    }

    @Test
    void documentsInTheRightPlaceAreNotFlagged() {
        List<ExtractedFact> policyNumber = List.of(
                new ExtractedFact(FactKey.POLICY_NUMBER, "CV-88213-02", "Group policy number: CV-88213-02", 1, true));
        assertThat(KindCheck.warning(DocumentKind.POLICY, POLICY, policyNumber)).isEmpty();

        List<ExtractedFact> amount = List.of(
                new ExtractedFact(FactKey.AMOUNT_CHARGED, "120.00", "Total charged: $120.00", 1, true));
        assertThat(KindCheck.warning(DocumentKind.RECEIPT, RECEIPT, amount)).isEmpty();

        // A real policy with an invoice page (a car policy renewal) has its policy number.
        List<PageText> renewal = List.of(new PageText(1, "INVOICE. Total charged $1,782.53. Policy Number: 030326674"));
        List<ExtractedFact> carNumber = List.of(
                new ExtractedFact(FactKey.POLICY_NUMBER, "030326674", "Policy Number: 030326674", 1, true));
        assertThat(KindCheck.warning(DocumentKind.POLICY, renewal, carNumber)).isEmpty();
    }
}

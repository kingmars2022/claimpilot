package com.claimpilot.samples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** The samples are reproducible, and the forms shipped with the app are the generator's output. */
class SampleDocumentsTest {

    private static final Map<String, Supplier<byte[]>> SHIPPED_FORMS = Map.of(
            SampleDocuments.CLAIM_FORM, SampleDocuments::claimForm,
            SampleDocuments.FRENCH_CLAIM_FORM, SampleDocuments::frenchClaimForm,
            SampleDocuments.NORTHGATE_FORM, SampleDocuments::northgateClaimForm,
            SampleDocuments.DRUG_FORM, SampleDocuments::drugClaimForm,
            SampleDocuments.VISION_FORM, SampleDocuments::visionClaimForm,
            SampleDocuments.DETAILS_SHEET, SampleDocuments::claimDetailsSheet);

    @Test
    void generatingTwiceGivesTheSameBytes() {
        assertThat(SampleDocuments.spousePolicy()).isEqualTo(SampleDocuments.spousePolicy());
        assertThat(SampleDocuments.bookletPolicy()).isEqualTo(SampleDocuments.bookletPolicy());
        assertThat(SampleDocuments.receiptPdf()).isEqualTo(SampleDocuments.receiptPdf());
        assertThat(SampleDocuments.claimForm()).isEqualTo(SampleDocuments.claimForm());
    }

    @Test
    void theShippedFormsAreUpToDate() throws IOException {
        for (var form : SHIPPED_FORMS.entrySet()) {
            try (InputStream in = new ClassPathResource("forms/" + form.getKey()).getInputStream()) {
                assertThat(in.readAllBytes()).as(form.getKey() + ": run SampleDocuments.main to regenerate")
                        .isEqualTo(form.getValue().get());
            }
        }
    }
}

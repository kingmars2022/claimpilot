package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.ExtractedFact;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.Profile;
import com.claimpilot.user.ProfileDto;

class ClaimValueAssemblerTest {

    private static final UUID SPOUSE = UUID.randomUUID();
    private static final UUID OWN = UUID.randomUUID();
    private static final UUID RECEIPT = UUID.randomUUID();

    @Test
    void eachItemComesFromTheRightDocument() {
        Map<DataKey, DraftValue> values = ClaimValueAssembler.assemble(
                new ClaimValueAssembler.Source(SPOUSE, "spouse.pdf", Map.of(
                        FactKey.PLAN_MEMBER_NAME, fact(SPOUSE, FactKey.PLAN_MEMBER_NAME, "Marc Gagnon", 1, true),
                        FactKey.POLICY_NUMBER, fact(SPOUSE, FactKey.POLICY_NUMBER, "CV-88213-02", 1, true))),
                new ClaimValueAssembler.Source(OWN, "own.pdf", Map.of(
                        FactKey.INSURER_NAME, fact(OWN, FactKey.INSURER_NAME, "Harbourline Vie", 1, true))),
                new ClaimValueAssembler.Source(RECEIPT, "receipt.pdf", Map.of(
                        FactKey.AMOUNT_CHARGED, fact(RECEIPT, FactKey.AMOUNT_CHARGED, "120.00", 1, true),
                        FactKey.AMOUNT_PAID_BY_OTHER_PLAN,
                        fact(RECEIPT, FactKey.AMOUNT_PAID_BY_OTHER_PLAN, "84.00", 1, false))),
                profile(), Relationship.SPOUSE);

        assertThat(values.get(DataKey.MEMBER_NAME)).satisfies(v -> {
            assertThat(v.value()).isEqualTo("Marc Gagnon");
            assertThat(v.sourceType()).isEqualTo(SourceType.POLICY);
            assertThat(v.sourceLabel()).isEqualTo("spouse.pdf, page 1");
        });
        assertThat(values.get(DataKey.OTHER_INSURER).sourceType()).isEqualTo(SourceType.OTHER_POLICY);
        assertThat(values.get(DataKey.PATIENT_NAME).sourceType()).isEqualTo(SourceType.PROFILE);
        assertThat(values.get(DataKey.PATIENT_DOB).value()).isEqualTo("1991-04-17");
        assertThat(values.get(DataKey.PATIENT_ADDRESS).value()).isEqualTo("4820 rue Fabre, Montreal QC H2J 3W1");
        assertThat(values.get(DataKey.PATIENT_RELATIONSHIP).value()).isEqualTo("Spouse");
        assertThat(values.get(DataKey.CERTIFICATE_NUMBER).sourceType()).isEqualTo(SourceType.MISSING);

        DraftValue claimed = values.get(DataKey.AMOUNT_CLAIMED);
        assertThat(claimed.value()).isEqualTo("36.00");
        assertThat(claimed.sourceType()).isEqualTo(SourceType.CALCULATED);
        assertThat(claimed.verified()).as("built on an unverified amount").isFalse();
    }

    @Test
    void amountClaimedIsTheFullAmountWhenNothingWasPaidFirst() {
        DraftValue claimed = ClaimValueAssembler.amountClaimed(
                new DraftValue("120.00", SourceType.RECEIPT, RECEIPT, "r", 1, "q", true), DraftValue.missing());
        assertThat(claimed.value()).isEqualTo("120.00");
        assertThat(ClaimValueAssembler.amountClaimed(DraftValue.missing(), DraftValue.missing()).sourceType())
                .isEqualTo(SourceType.MISSING);
    }

    private static DocumentFact fact(UUID doc, FactKey key, String value, Integer page, boolean verified) {
        return new DocumentFact(doc, new ExtractedFact(key, value, value, page, verified));
    }

    private static Profile profile() {
        Profile profile = new Profile(1L);
        profile.update(new ProfileDto("Fiona Tremblay", LocalDate.of(1991, 4, 17), "4820 rue Fabre", "Montreal", "QC",
                "H2J 3W1", null));
        return profile;
    }
}

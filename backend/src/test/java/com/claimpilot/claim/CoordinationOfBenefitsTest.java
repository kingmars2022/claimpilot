package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.claimpilot.claim.CoordinationOfBenefits.Decision;
import com.claimpilot.claim.CoordinationOfBenefits.Household;
import com.claimpilot.claim.CoordinationOfBenefits.Patient;
import com.claimpilot.claim.CoordinationOfBenefits.Plan;

class CoordinationOfBenefitsTest {

    private static final Plan FIONAS = new Plan(UUID.randomUUID(), "Harbourline Vie", "Fiona Tremblay", "HL-204518");
    private static final Plan MARCS = new Plan(UUID.randomUUID(), "Cedarview Assurance", "Marc Gagnon", "CV-88213-02");
    private static final Household HOUSEHOLD = new Household("Fiona Tremblay", LocalDate.of(1991, 4, 17),
            "Marc Gagnon", LocalDate.of(1989, 11, 2));

    @Test
    void theMembersOwnPlanPaysFirstAndTheBalanceGoesToTheSpousesPlan() {
        Decision decision = CoordinationOfBenefits.decide(Patient.ME, List.of(MARCS, FIONAS), HOUSEHOLD);

        assertThat(decision.decided()).isTrue();
        assertThat(decision.firstPolicyId()).isEqualTo(FIONAS.policyId());
        assertThat(decision.secondPolicyId()).isEqualTo(MARCS.policyId());
        assertThat(decision.relationshipOnSecond()).isEqualTo(Relationship.SPOUSE);
    }

    @Test
    void forTheSpouseTheirOwnPlanPaysFirst() {
        Decision decision = CoordinationOfBenefits.decide(Patient.SPOUSE, List.of(FIONAS, MARCS), HOUSEHOLD);

        assertThat(decision.firstPolicyId()).isEqualTo(MARCS.policyId());
        assertThat(decision.secondPolicyId()).isEqualTo(FIONAS.policyId());
    }

    @Test
    void forAChildTheParentWhoseBirthdayComesFirstInTheYearPaysFirst() {
        Decision decision = CoordinationOfBenefits.decide(Patient.CHILD, List.of(MARCS, FIONAS), HOUSEHOLD);

        // April 17 (Fiona) comes before November 2 (Marc), whatever the birth years.
        assertThat(decision.firstPolicyId()).isEqualTo(FIONAS.policyId());
        assertThat(decision.relationshipOnSecond()).isEqualTo(Relationship.CHILD);
        assertThat(decision.explanation()).contains("April 17").contains("custody");
    }

    @Test
    void withTheSameBirthdayTheFirstNameThatComesFirstAlphabeticallyDecides() {
        Household sameDay = new Household("Fiona Tremblay", LocalDate.of(1991, 6, 1), "Marc Gagnon",
                LocalDate.of(1985, 6, 1));

        Decision decision = CoordinationOfBenefits.decide(Patient.CHILD, List.of(MARCS, FIONAS), sameDay);

        assertThat(decision.firstPolicyId()).as("Fiona before Marc").isEqualTo(FIONAS.policyId());
    }

    @Test
    void missingInformationIsReportedNotGuessed() {
        Household noBirthdays = new Household("Fiona Tremblay", null, "Marc Gagnon", null);
        assertThat(CoordinationOfBenefits.decide(Patient.CHILD, List.of(MARCS, FIONAS), noBirthdays).decided())
                .isFalse();

        Household noSpouse = new Household("Fiona Tremblay", null, null, null);
        Decision decision = CoordinationOfBenefits.decide(Patient.ME, List.of(MARCS, FIONAS), noSpouse);
        assertThat(decision.decided()).isFalse();
        assertThat(decision.explanation()).contains("spouse's name");
    }

    @Test
    void theSamePolicyUploadedTwiceIsOnePlanButTwoPlansOfOnePersonAreUndecided() {
        Plan copy = new Plan(UUID.randomUUID(), "Harbourline Vie", "Fiona Tremblay", "HL 204518");
        assertThat(CoordinationOfBenefits.decide(Patient.ME, List.of(FIONAS, copy, MARCS), HOUSEHOLD).decided())
                .isTrue();

        Plan secondJob = new Plan(UUID.randomUUID(), "Other Insurer", "Fiona Tremblay", "OT-1");
        Decision decision = CoordinationOfBenefits.decide(Patient.ME, List.of(FIONAS, secondJob, MARCS), HOUSEHOLD);
        assertThat(decision.decided()).isFalse();
        assertThat(decision.explanation()).contains("full-time");
    }

    @Test
    void namesAreComparedWithoutAccentsOrCase() {
        Household accents = new Household("FIONA TREMBLAY", null, "marc gagnón", null);

        assertThat(CoordinationOfBenefits.decide(Patient.ME, List.of(MARCS, FIONAS), accents).decided()).isTrue();
    }
}

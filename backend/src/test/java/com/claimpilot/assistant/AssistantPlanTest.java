package com.claimpilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.claimpilot.claim.ClaimType;
import com.claimpilot.claim.Relationship;
import com.claimpilot.extraction.FactKey;

class AssistantPlanTest {

    private static final AssistantContext.Doc SPOUSE = new AssistantContext.Doc(UUID.randomUUID(), "cedarview.pdf",
            Map.of(FactKey.INSURER_NAME, "Cedarview Assurance", FactKey.PLAN_MEMBER_NAME, "Marc Gagnon"));
    private static final AssistantContext.Doc OWN = new AssistantContext.Doc(UUID.randomUUID(), "harbourline.pdf",
            Map.of(FactKey.INSURER_NAME, "Harbourline Vie", FactKey.PLAN_MEMBER_NAME, "Fiona Tremblay"));
    private static final AssistantContext.Doc RECEIPT = new AssistantContext.Doc(UUID.randomUUID(), "receipt.png",
            Map.of(FactKey.SERVICE_TYPE, "Physiotherapy - follow-up treatment"));

    private static final AssistantContext BOTH = new AssistantContext(List.of(SPOUSE, OWN), List.of(RECEIPT),
            "Fiona Tremblay");

    @Test
    void aClaimAlwaysComesWithItsGuideAndUnknownActionsAreDropped() {
        AssistantPlan plan = plan("{\"steps\": [\"FILL\", \"SUBMIT_TO_INSURER\", \"ASK\"], \"policy\": 1}", BOTH);

        assertThat(plan.actions()).containsExactly(AssistantAction.ASK, AssistantAction.GUIDE, AssistantAction.FILL);
    }

    @Test
    void theRestOfTheClaimIsWorkedOutByCode() {
        AssistantPlan plan = plan("{\"steps\": [\"FILL\"], \"policy\": 1}", BOTH);

        assertThat(plan.clarify()).isNull();
        assertThat(plan.policyId()).isEqualTo(SPOUSE.id());
        assertThat(plan.otherPolicyId()).as("the other of two policies").isEqualTo(OWN.id());
        assertThat(plan.receiptId()).isEqualTo(RECEIPT.id());
        assertThat(plan.claimType()).as("from the receipt").isEqualTo(ClaimType.SECONDARY_PARAMEDICAL);
        assertThat(plan.relationship()).as("Fiona is not the member of Marc's plan").isEqualTo(Relationship.SPOUSE);
    }

    @Test
    void anInventedPolicyNumberBecomesAQuestionBack() {
        AssistantPlan plan = plan("{\"steps\": [\"FILL\"], \"policy\": 7}", BOTH);

        assertThat(plan.policyId()).isNull();
        assertThat(plan.clarify().field()).isEqualTo("policyId");
        assertThat(plan.clarify().options()).hasSize(2);
    }

    @Test
    void withOnePolicyThereIsNothingToAsk() {
        AssistantContext one = new AssistantContext(List.of(OWN), List.of(), "Fiona Tremblay");

        AssistantPlan plan = plan("{\"steps\": [\"ASK\"], \"question\": \"Is massage covered?\"}", one);

        assertThat(plan.policyId()).isEqualTo(OWN.id());
        assertThat(plan.question()).isEqualTo("Is massage covered?");
        assertThat(plan.relationship()).isEqualTo(Relationship.SELF);
    }

    @Test
    void aGuideWithoutAKnownKindOfCareAsksForIt() {
        AssistantContext noReceipt = new AssistantContext(List.of(OWN), List.of(), "Fiona Tremblay");

        AssistantPlan plan = AssistantPlan.from("{\"steps\": [\"GUIDE\"]}", noReceipt,
                new AssistantDtos.Request("How do I make a claim?", null, null));

        assertThat(plan.clarify().field()).isEqualTo("claimType");
    }

    @Test
    void anythingElseIsNotAnAction() {
        assertThat(plan("Sure! Here is a poem about spring.", BOTH).actions()).isEmpty();
    }

    @Test
    void kindOfCareIsRecognisedInSeveralLanguages() {
        assertThat(AssistantPlan.inferClaimType("Mes lunettes ont coûté 300 $")).isEqualTo(ClaimType.SECONDARY_VISION);
        assertThat(AssistantPlan.inferClaimType("我的牙医账单")).isEqualTo(ClaimType.SECONDARY_DENTAL);
        assertThat(AssistantPlan.inferClaimType("médicaments sur ordonnance")).isEqualTo(ClaimType.SECONDARY_DRUGS);
        assertThat(AssistantPlan.inferClaimType("hello")).isNull();
    }

    private static AssistantPlan plan(String reply, AssistantContext context) {
        return AssistantPlan.from(reply, context,
                new AssistantDtos.Request("My physio bill was $120, claim the rest on Marc's plan", null, null));
    }
}

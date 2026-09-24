package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/** Phase 4: one message, several modules chained by the assistant. */
class AssistantIntegrationTest extends IntegrationTestBase {

    private String token;
    private String spousePolicy;

    @BeforeEach
    void uploadDocuments() throws Exception {
        token = login("fiona");
        spousePolicy = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());
        upload(token, "policies", SampleDocuments.OWN_POLICY, SampleDocuments.ownPolicyFrench());
        upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf());
    }

    @Test
    void aBillBecomesAGuideAndAPrefilledFormAfterOneQuestionBack() throws Exception {
        String message = "My physio cost $120 and my plan paid $84. Can I claim the rest on my husband's plan?";
        chatModel.nextPlan = "{\"steps\": [\"FILL\"], \"claimType\": null}";

        String first = assistant(message, null);

        // Two policies and no choice in the plan: the assistant asks rather than guessing.
        assertThat((List<String>) JsonPath.read(first, "$.steps[*].type")).containsExactly("CLARIFY");
        assertThat((List<String>) JsonPath.read(first, "$.steps[0].clarify.options[*].value")).contains(spousePolicy);

        String second = assistant(message, spousePolicy);

        assertThat((List<String>) JsonPath.read(second, "$.actions")).containsExactly("GUIDE", "FILL");
        assertThat((List<String>) JsonPath.read(second, "$.steps[*].type")).containsExactly("GUIDE", "CLAIM");
        assertThat((String) JsonPath.read(second, "$.steps[1].draft.relationship")).isEqualTo("SPOUSE");
        assertThat((List<String>) JsonPath.read(second, "$.steps[1].draft.fields[?(@.key == 'OTHER_INSURER')].value"))
                .containsExactly("Harbourline Vie");
        assertThat((List<String>) JsonPath.read(second, "$.steps[1].draft.fields[?(@.key == 'AMOUNT_CLAIMED')].value"))
                .containsExactly("36.00");
        assertThat((String) JsonPath.read(second, "$.summary")).contains("pre-filled the claim form");
    }

    @Test
    void aClaimOnThePlanThatPaysFirstIsMovedToThePlanThatPaysSecond() throws Exception {
        // The model picks Fiona's own plan (the newest upload, number 1), which pays first.
        chatModel.nextPlan = "{\"steps\": [\"FILL\"], \"policy\": 1, \"claimType\": \"SECONDARY_PARAMEDICAL\"}";

        String reply = assistant("Claim my physio receipt", null);

        assertThat((List<String>) JsonPath.read(reply, "$.steps[*].type")).containsExactly("GUIDE", "NOTE", "CLAIM");
        assertThat((String) JsonPath.read(reply, "$.steps[2].draft.policy.id")).isEqualTo(spousePolicy);
        assertThat((String) JsonPath.read(reply, "$.steps[1].text")).contains("pays first");
    }

    @Test
    void aQuestionIsAnsweredWithItsCitations() throws Exception {
        chatModel.nextPlan = "{\"steps\": [\"ASK\"], \"policy\": 2, "
                + "\"question\": \"What is the physiotherapist maximum per calendar year per person?\"}";

        String reply = assistant("What is the physiotherapist maximum on Marc's plan?", null);

        assertThat((List<String>) JsonPath.read(reply, "$.steps[*].type")).containsExactly("ANSWER");
        assertThat((String) JsonPath.read(reply, "$.steps[0].answer.status")).isEqualTo("ANSWERED");
    }

    @Test
    void somethingElseGetsAShortExplanation() throws Exception {
        String reply = assistant("Write me a poem", null);

        assertThat((List<String>) JsonPath.read(reply, "$.actions")).isEmpty();
        assertThat((List<String>) JsonPath.read(reply, "$.steps[*].type")).containsExactly("MESSAGE");
    }

    private String assistant(String message, String policyId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("message", message));
        if (policyId != null) {
            body.put("policyId", policyId);
        }
        return mvc.perform(as(token, post("/api/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}

package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;
import com.claimpilot.support.IntegrationTestBase.FakeChatModel.Kind;

/** Module 1: upload a policy, read its key facts, and ask questions with three answer states. */
class PolicyIntegrationTest extends IntegrationTestBase {

    private String token;
    private String policyId;

    @BeforeEach
    void uploadSpousePolicy() throws Exception {
        token = login("fiona");
        policyId = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());
        chatModel.prompts.clear();
    }

    @Test
    void keyFactsAreVerifiedAgainstThePolicyText() throws Exception {
        String body = mvc.perform(as(token, get("/api/policies/" + policyId))).andReturn().getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
        assertThat((Integer) JsonPath.read(body, "$.chunkCount")).isPositive();
        Map<String, Object> policyNumber = fact(body, "POLICY_NUMBER");
        assertThat(policyNumber).containsEntry("value", "CV-88213-02").containsEntry("page", 1)
                .containsEntry("verified", true);
        // The fake model invented a claims address; code could not find it in the policy.
        Map<String, Object> address = fact(body, "CLAIMS_ADDRESS");
        assertThat(address).containsEntry("verified", false).containsEntry("page", null);
    }

    @Test
    void answeredQuestionCitesThePolicyPage() throws Exception {
        String answer = ask(token, policyId,
                "What is the physiotherapist maximum per calendar year per person?", null);

        assertThat((String) JsonPath.read(answer, "$.status")).isEqualTo("ANSWERED");
        assertThat((String) JsonPath.read(answer, "$.language")).isEqualTo("en");
        assertThat((Integer) JsonPath.read(answer, "$.citations[0].page")).isEqualTo(2);
        assertThat((Object) JsonPath.read(answer, "$.callKit")).isNull();
        assertThat(chatModel.calls(Kind.ANSWER)).singleElement().asString()
                .contains("(page 2)").contains("Physiotherapist: up to $600");
    }

    @Test
    void questionTheyPolicyDoesNotCoverGetsACallKitWithoutAModelCall() throws Exception {
        String answer = ask(token, policyId, "Is laser eye surgery included?", null);

        assertThat((String) JsonPath.read(answer, "$.status")).isEqualTo("NOT_IN_POLICY");
        assertThat((List<?>) JsonPath.read(answer, "$.citations")).isEmpty();
        assertThat((String) JsonPath.read(answer, "$.callKit.phone.value")).isEqualTo("1-800-555-0199");
        assertThat((Integer) JsonPath.read(answer, "$.callKit.phone.page")).isEqualTo(1);
        assertThat((String) JsonPath.read(answer, "$.callKit.hours.value")).startsWith("Monday to Friday");
        assertThat((String) JsonPath.read(answer, "$.callKit.script"))
                .contains("group policy number CV-88213-02")
                .contains("certificate number 55190336")
                .contains("My question is: Is laser eye surgery included?");
        assertThat(chatModel.calls(Kind.ANSWER)).isEmpty();
    }

    @Test
    void unclearAnswerShowsClausesAndACallKit() throws Exception {
        chatModel.nextAnswer = "STATUS: UNCLEAR\nKinesiologist treatments may be considered in a rehabilitation program [1].";

        String answer = ask(token, policyId,
                "Are kinesiologist treatments covered as part of a rehabilitation program?", null);

        assertThat((String) JsonPath.read(answer, "$.status")).isEqualTo("UNCLEAR");
        assertThat((List<?>) JsonPath.read(answer, "$.citations")).isNotEmpty();
        assertThat((String) JsonPath.read(answer, "$.callKit.phone.value")).isEqualTo("1-800-555-0199");
    }

    @Test
    void answerThatCitesNothingIsNotTrustedAsAnswered() throws Exception {
        chatModel.nextAnswer = "STATUS: ANSWERED\nYes, it is covered.";

        String answer = ask(token, policyId,
                "What is the physiotherapist maximum per calendar year per person?", null);

        assertThat((String) JsonPath.read(answer, "$.status")).isEqualTo("UNCLEAR");
        assertThat((List<?>) JsonPath.read(answer, "$.citations")).isNotEmpty();
    }

    @Test
    void frenchQuestionGetsAFrenchReplyAndAnEnglishCallScript() throws Exception {
        String answer = ask(token, policyId, "Est-ce que la physiothérapie est remboursée par mon régime ?", null);

        assertThat((String) JsonPath.read(answer, "$.language")).isEqualTo("fr");
        assertThat((String) JsonPath.read(answer, "$.status")).isEqualTo("NOT_IN_POLICY");
        assertThat((String) JsonPath.read(answer, "$.answer")).startsWith("Votre police ne répond pas");
        assertThat((String) JsonPath.read(answer, "$.callKit.script"))
                .contains("My question is: Is physiotherapy covered by my plan?");
        assertThat(chatModel.calls(Kind.TRANSLATE)).hasSize(1);
    }

    @Test
    void followUpKeepsThePolicyAndTheConversation() throws Exception {
        String first = ask(token, policyId, "What is the physiotherapist maximum per calendar year per person?", null);
        String conversationId = JsonPath.read(first, "$.conversationId");

        String second = ask(token, policyId, "And the chiropractor?", conversationId);

        assertThat((String) JsonPath.read(second, "$.conversationId")).isEqualTo(conversationId);
        assertThat((String) JsonPath.read(second, "$.policyId")).isEqualTo(policyId);
        assertThat(chatModel.calls(Kind.ANSWER).getLast()).contains("Earlier in this conversation:");
        String saved = mvc.perform(as(token, get("/api/conversations/" + conversationId))).andReturn().getResponse()
                .getContentAsString();
        assertThat((Integer) JsonPath.read(saved, "$.messages.length()")).isEqualTo(4);
        assertThat((String) JsonPath.read(saved, "$.messages[1].status")).isEqualTo("ANSWERED");
    }

    private static Map<String, Object> fact(String documentJson, String key) {
        List<Map<String, Object>> matches = JsonPath.read(documentJson, "$.facts[?(@.key == '" + key + "')]");
        assertThat(matches).as("fact " + key).hasSize(1);
        return matches.getFirst();
    }
}

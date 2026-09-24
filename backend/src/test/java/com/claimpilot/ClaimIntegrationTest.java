package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;
import com.claimpilot.support.IntegrationTestBase.FakeChatModel.Kind;

/**
 * Modules 2 and 3: a second-plan claim. Fiona's physiotherapy was paid in part by her own plan
 * (Harbourline, in French); the balance is claimed on her husband's plan (Cedarview).
 */
class ClaimIntegrationTest extends IntegrationTestBase {

    private String token;
    private String spousePolicy;
    private String ownPolicy;
    private String receipt;

    @BeforeEach
    void uploadDocuments() throws Exception {
        token = login("fiona");
        spousePolicy = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());
        ownPolicy = upload(token, "policies", SampleDocuments.OWN_POLICY, SampleDocuments.ownPolicyFrench());
        receipt = upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf());
        chatModel.prompts.clear();
    }

    @Test
    void guideListsCitedStepsAndIsReused() throws Exception {
        String url = "/api/claims/guide?policyId=" + spousePolicy + "&type=SECONDARY_PARAMEDICAL";
        String guide = mvc.perform(as(token, get(url))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Boolean) JsonPath.read(guide, "$.found")).isTrue();
        // The item citing a clause that does not exist was dropped.
        assertThat((List<?>) JsonPath.read(guide, "$.deadlines")).hasSize(1);
        assertThat((String) JsonPath.read(guide, "$.deadlines[0].text")).contains("12 months");
        assertThat((Object) JsonPath.read(guide, "$.deadlines[0].page")).isNotNull();
        assertThat((List<?>) JsonPath.read(guide, "$.documents")).hasSize(2);
        assertThat((String) JsonPath.read(guide, "$.preApproval.required")).isEqualTo("NO");

        mvc.perform(as(token, get(url))).andExpect(status().isOk());
        assertThat(chatModel.calls(Kind.GUIDE)).as("second request served from the cache").hasSize(1);
    }

    @Test
    void formIsPrefilledWithSourcesReviewedAndDownloaded() throws Exception {
        String draft = createDraft();
        String draftId = JsonPath.read(draft, "$.id");

        assertThat(field(draft, "MEMBER_NAME")).containsEntry("value", "Marc Gagnon")
                .containsEntry("sourceType", "POLICY").containsEntry("page", 1).containsEntry("verified", true);
        assertThat(field(draft, "OTHER_INSURER")).containsEntry("value", "Harbourline Vie")
                .containsEntry("sourceType", "OTHER_POLICY");
        assertThat(field(draft, "OTHER_POLICY_NUMBER")).containsEntry("value", "HL-204518");
        assertThat(field(draft, "PATIENT_NAME")).containsEntry("value", "Fiona Tremblay")
                .containsEntry("sourceType", "PROFILE");
        assertThat(field(draft, "PATIENT_RELATIONSHIP")).containsEntry("value", "Spouse");
        assertThat(field(draft, "SERVICE_DATE")).containsEntry("value", "2026-03-05")
                .containsEntry("sourceType", "RECEIPT");
        assertThat(field(draft, "AMOUNT_CLAIMED")).containsEntry("value", "36.00")
                .containsEntry("sourceType", "CALCULATED");
        List<String> keys = JsonPath.read(draft, "$.fields[*].key");
        assertThat(keys).doesNotContain("SIGNATURE", "DECLARATION", "SIGNATURE_DATE", "NONE");
        assertThat((List<String>) JsonPath.read(draft, "$.leftForYou")).contains(
                "Signature of plan member",
                "I declare that the information on this claim is true and complete",
                "Date signed (YYYY-MM-DD)",
                "Bank transit and account number for direct deposit");

        // Not downloadable until every field has been reviewed.
        mvc.perform(as(token, get("/api/claims/" + draftId + "/pdf"))).andExpect(status().isBadRequest());

        String corrected = review(draftId, "SERVICE_TYPE", "Physiotherapy");
        assertThat(field(corrected, "SERVICE_TYPE")).containsEntry("sourceType", "USER")
                .containsEntry("value", "Physiotherapy");
        String last = corrected;
        for (String key : keys) {
            last = review(draftId, key, null);
        }
        assertThat((Boolean) JsonPath.read(last, "$.readyToDownload")).isTrue();

        byte[] pdf = mvc.perform(as(token, get("/api/claims/" + draftId + "/pdf")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertThat(form.getField("txtField_01").getValueAsString()).isEqualTo("Marc Gagnon");
            assertThat(form.getField("txtField_09").getValueAsString()).isEqualTo("Harbourline Vie");
            assertThat(form.getField("txtField_14").getValueAsString()).isEqualTo("Physiotherapy");
            assertThat(form.getField("txtField_17").getValueAsString()).isEqualTo("36.00");
            // The model mapped the signature to the member's name; it must still be blank.
            assertThat(form.getField("sigField_01").getValueAsString()).isEmpty();
            assertThat(form.getField("txtField_18").getValueAsString()).isEmpty();
            assertThat(form.getField("txtField_19").getValueAsString()).isEmpty();
            assertThat(((PDCheckBox) form.getField("chkField_01")).isChecked()).isFalse();
        }
    }

    @Test
    void fieldMappingIsWorkedOutOnceAndReused() throws Exception {
        createDraft();
        int mappingCalls = chatModel.calls(Kind.MAP_FORM).size();
        assertThat(mappingCalls).isLessThanOrEqualTo(1);

        createDraft();
        createDraft();
        assertThat(chatModel.calls(Kind.MAP_FORM)).hasSize(mappingCalls);
    }

    @Test
    void anotherUserCannotSeeOrUseTheseDocuments() throws Exception {
        String draftId = JsonPath.read(createDraft(), "$.id");
        String sam = login("sam");

        mvc.perform(as(sam, get("/api/claims/" + draftId))).andExpect(status().isNotFound());
        mvc.perform(as(sam, get("/api/policies/" + spousePolicy))).andExpect(status().isNotFound());
        mvc.perform(as(sam, get("/api/receipts/" + receipt))).andExpect(status().isNotFound());
        mvc.perform(as(sam, get("/api/claims/guide?policyId=" + spousePolicy + "&type=SECONDARY_DENTAL")))
                .andExpect(status().isNotFound());
        mvc.perform(as(sam, post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "Is physio covered?", "policyId", spousePolicy)))))
                .andExpect(status().isNotFound());
        mvc.perform(as(sam, post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("claimType", "SECONDARY_PARAMEDICAL", "policyId", spousePolicy,
                                "relationship", "SELF")))))
                .andExpect(status().isNotFound());
        String samPolicies = mvc.perform(as(sam, get("/api/policies"))).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(samPolicies, "$[*].id")).doesNotContain(spousePolicy, ownPolicy);
    }

    private String createDraft() throws Exception {
        return mvc.perform(as(token, post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("claimType", "SECONDARY_PARAMEDICAL", "policyId", spousePolicy,
                                "otherPolicyId", ownPolicy, "receiptId", receipt, "relationship", "SPOUSE")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String review(String draftId, String key, String value) throws Exception {
        Map<String, Object> body = value == null ? Map.of("reviewed", true) : Map.of("value", value, "reviewed", true);
        return mvc.perform(as(token, patch("/api/claims/" + draftId + "/fields/" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static Map<String, Object> field(String draftJson, String key) {
        List<Map<String, Object>> matches = JsonPath.read(draftJson, "$.fields[?(@.key == '" + key + "')]");
        assertThat(matches).as("field " + key).hasSize(1);
        return matches.getFirst();
    }
}

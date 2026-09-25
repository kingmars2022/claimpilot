package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.claimpilot.claim.FormCatalog;
import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/** Phase 2: a claim filled on the insurer's own fillable PDF, uploaded by the user. */
class FormIntegrationTest extends IntegrationTestBase {

    private String token;

    @Autowired
    private FormCatalog catalog;

    @BeforeEach
    void signIn() throws Exception {
        token = login("fiona");
    }

    @Test
    void claimIsFilledOnAnUploadedFrenchForm() throws Exception {
        String spousePolicy = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());
        String ownPolicy = upload(token, "policies", SampleDocuments.OWN_POLICY, SampleDocuments.ownPolicyFrench());
        String receipt = upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf());
        String formId = upload(token, "forms", SampleDocuments.FRENCH_CLAIM_FORM, SampleDocuments.frenchClaimForm());

        String forms = mvc.perform(as(token, get("/api/forms"))).andReturn().getResponse().getContentAsString();
        List<String> keys = JsonPath.read(forms, "$[*].key");
        assertThat(keys).contains("builtin:cedarview-secondary", "builtin:harbourline-demande", "upload:" + formId);
        List<Integer> fieldCount = JsonPath.read(forms, "$[?(@.key == 'upload:" + formId + "')].fieldCount");
        assertThat(fieldCount).containsExactly(20);

        String draft = mvc.perform(as(token, post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("claimType", "SECONDARY_PARAMEDICAL", "policyId", spousePolicy,
                                "otherPolicyId", ownPolicy, "receiptId", receipt, "relationship", "SPOUSE",
                                "formKey", "upload:" + formId)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String draftId = JsonPath.read(draft, "$.id");

        assertThat((String) JsonPath.read(draft, "$.form.name")).isEqualTo(SampleDocuments.FRENCH_CLAIM_FORM);
        List<String> fields = JsonPath.read(draft, "$.fields[*].key");
        assertThat(fields).contains("PATIENT_PHONE", "RECEIPT_NUMBER").doesNotContain("PLAN_SPONSOR");
        // The model mapped "Date de la signature" to a date of birth; the signature rule keeps it blank.
        assertThat((List<String>) JsonPath.read(draft, "$.leftForYou"))
                .contains("Date de la signature (AAAA-MM-JJ)", "Signature de l'adhérent");

        for (String key : fields) {
            mvc.perform(as(token, patch("/api/claims/" + draftId + "/fields/" + key)
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reviewed", true)))))
                    .andExpect(status().isOk());
        }
        byte[] pdf = mvc.perform(as(token, get("/api/claims/" + draftId + "/pdf")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertThat(form.getField("f01").getValueAsString()).isEqualTo("Marc Gagnon");
            assertThat(form.getField("f08").getValueAsString()).isEqualTo("514-555-0142");
            assertThat(form.getField("f12").getValueAsString()).isEqualTo("R-2026-0318");
            assertThat(form.getField("f17").getValueAsString()).isEqualTo("36.00");
            assertThat(form.getField("f19").getValueAsString()).isEmpty();
            assertThat(form.getField("f20").getValueAsString()).isEmpty();
        }

        assertThat(catalog.isLoaded(java.util.UUID.fromString(formId))).isTrue();
        mvc.perform(as(token, delete("/api/forms/" + formId))).andExpect(status().isNoContent());
        assertThat(catalog.isLoaded(java.util.UUID.fromString(formId))).as("dropped from memory").isFalse();
    }

    @Test
    void aPdfWithoutFillableFieldsIsRejectedAsAForm() throws Exception {
        String body = mvc.perform(as(token, multipart("/api/forms").file(new MockMultipartFile("file",
                        "policy.pdf", "application/pdf", SampleDocuments.spousePolicy()))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        String status = "UPLOADED";
        String form = body;
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!"FAILED".equals(status) && !"READY".equals(status) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            form = mvc.perform(as(token, get("/api/forms/" + id))).andReturn().getResponse().getContentAsString();
            status = JsonPath.read(form, "$.status");
        }
        assertThat(status).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(form, "$.errorMessage")).contains("no fillable fields").contains("Claim details sheet");
    }

    @Test
    void anotherUsersFormCannotBeUsed() throws Exception {
        String formId = upload(token, "forms", SampleDocuments.FRENCH_CLAIM_FORM, SampleDocuments.frenchClaimForm());
        String sam = login("sam");
        String samPolicy = upload(sam, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());

        mvc.perform(as(sam, post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("claimType", "SECONDARY_VISION", "policyId", samPolicy,
                                "relationship", "SELF", "formKey", "upload:" + formId)))))
                .andExpect(status().isNotFound());
        mvc.perform(as(sam, delete("/api/policies/" + samPolicy))).andExpect(status().isNoContent());
    }
}

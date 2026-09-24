package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/** Scanned PDFs and photos are read with Tesseract. Skipped when Tesseract is not installed. */
class ScannedDocumentIntegrationTest extends IntegrationTestBase {

    @BeforeAll
    static void requireTesseract() {
        boolean installed;
        try {
            installed = new ProcessBuilder("tesseract", "--version").start().waitFor() == 0;
        } catch (Exception ex) {
            installed = false;
        }
        assumeThat(installed).as("tesseract is installed").isTrue();
    }

    @Test
    void scannedPolicyIsReadWithOcrAndItsFactsVerified() throws Exception {
        String token = login("fiona");
        String id = upload(token, "policies", SampleDocuments.SPOUSE_POLICY_SCANNED,
                SampleDocuments.scanned(SampleDocuments.spousePolicy()));

        String body = mvc.perform(as(token, get("/api/policies/" + id))).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.chunkCount")).isPositive();
        List<Map<String, Object>> number = JsonPath.read(body, "$.facts[?(@.key == 'POLICY_NUMBER')]");
        assertThat(number.getFirst()).containsEntry("verified", true).containsEntry("page", 1);

        String answer = ask(token, id, "What is the physiotherapist maximum per calendar year per person?", null);
        assertThat((Integer) JsonPath.read(answer, "$.citations[0].page")).isEqualTo(2);
    }

    @Test
    void receiptPhotoIsReadWithOcr() throws Exception {
        String token = login("fiona");
        String id = upload(token, "receipts", SampleDocuments.RECEIPT_PNG, SampleDocuments.receiptPng());

        String body = mvc.perform(as(token, get("/api/receipts/" + id))).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> charged = JsonPath.read(body, "$.facts[?(@.key == 'AMOUNT_CHARGED')]");
        assertThat(charged.getFirst()).containsEntry("value", "120.00").containsEntry("verified", true);
    }
}

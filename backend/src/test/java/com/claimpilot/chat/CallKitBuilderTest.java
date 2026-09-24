package com.claimpilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.ExtractedFact;
import com.claimpilot.extraction.FactKey;

class CallKitBuilderTest {

    @Test
    void scriptIncludesTheNumbersAndTheQuestion() {
        UUID id = UUID.randomUUID();
        Map<FactKey, DocumentFact> facts = Map.of(
                FactKey.INSURER_NAME, fact(id, FactKey.INSURER_NAME, "Cedarview Assurance"),
                FactKey.INSURER_PHONE, fact(id, FactKey.INSURER_PHONE, "1-800-555-0199"),
                FactKey.POLICY_NUMBER, fact(id, FactKey.POLICY_NUMBER, "CV-88213-02"),
                FactKey.CERTIFICATE_NUMBER, fact(id, FactKey.CERTIFICATE_NUMBER, "55190336"),
                FactKey.PLAN_MEMBER_NAME, fact(id, FactKey.PLAN_MEMBER_NAME, "Marc Gagnon"));

        CallKit kit = CallKitBuilder.build(facts, "Is acupuncture covered");

        assertThat(kit.phone().value()).isEqualTo("1-800-555-0199");
        assertThat(kit.hours()).isNull();
        assertThat(kit.details()).extracting(CallKit.Detail::label)
                .containsExactly("Group policy number", "Certificate / member ID", "Plan member");
        assertThat(kit.script())
                .contains("The plan member is Marc Gagnon, group policy number CV-88213-02, certificate number 55190336")
                .contains("My question is: Is acupuncture covered?");
    }

    private static DocumentFact fact(UUID doc, FactKey key, String value) {
        return new DocumentFact(doc, new ExtractedFact(key, value, value, 1, true));
    }
}

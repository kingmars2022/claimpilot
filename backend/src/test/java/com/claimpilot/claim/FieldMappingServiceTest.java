package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.claimpilot.samples.SampleDocuments;

class FieldMappingServiceTest {

    private static final FormTemplate FORM = FormTemplate.of("test", SampleDocuments.claimForm());

    @Test
    void formFieldsAreReadWithTheirTooltips() {
        assertThat(FORM.fields()).hasSize(21);
        assertThat(FORM.fields().getFirst()).isEqualTo(
                new FormTemplate.Field("txtField_01", "Plan member's full name", false));
        assertThat(FORM.fields()).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("chkField_01");
            assertThat(f.checkbox()).isTrue();
        });
    }

    @Test
    void signatureAndDeclarationStayBlankWhateverTheModelSays() {
        String reply = """
                {"txtField_01": "MEMBER_NAME", "txtField_05": "patient_name", "txtField_17": "AMOUNT_CLAIMED",
                 "sigField_01": "MEMBER_NAME", "chkField_01": "PATIENT_NAME", "txtField_18": "SERVICE_DATE",
                 "txtField_19": "BANK_ACCOUNT", "txtField_02": "NOT_A_KEY"}
                """;

        Map<String, DataKey> mapping = FieldMappingService.parse(reply, FORM);

        assertThat(mapping).hasSize(FORM.fields().size());
        assertThat(mapping.get("txtField_01")).isEqualTo(DataKey.MEMBER_NAME);
        assertThat(mapping.get("txtField_05")).isEqualTo(DataKey.PATIENT_NAME);
        assertThat(mapping.get("sigField_01")).as("signature").isEqualTo(DataKey.NONE);
        assertThat(mapping.get("chkField_01")).as("declaration checkbox").isEqualTo(DataKey.NONE);
        assertThat(mapping.get("txtField_18")).as("date signed").isEqualTo(DataKey.NONE);
        assertThat(mapping.get("txtField_19")).as("unknown key").isEqualTo(DataKey.NONE);
        assertThat(mapping.get("txtField_02")).isEqualTo(DataKey.NONE);
        assertThat(mapping.get("txtField_03")).as("not in the reply").isEqualTo(DataKey.NONE);
    }

    @Test
    void certificateNumbersCanBeFilled() {
        String reply = """
                {"txtField_03": "CERTIFICATE_NUMBER", "txtField_11": "OTHER_CERTIFICATE_NUMBER"}
                """;

        Map<String, DataKey> mapping = FieldMappingService.parse(reply, FORM);

        assertThat(mapping.get("txtField_03")).isEqualTo(DataKey.CERTIFICATE_NUMBER);
        assertThat(mapping.get("txtField_11")).isEqualTo(DataKey.OTHER_CERTIFICATE_NUMBER);
        assertThat(FieldMappingService.PERSONAL_ATTESTATION.matcher("I certify that this is true").find()).isTrue();
    }

    @Test
    void promptListsItemsAndFields() {
        assertThat(FieldMappingService.prompt(FORM))
                .contains("- MEMBER_NAME:")
                .contains("- txtField_07: Patient's relationship to the plan member")
                .contains("(checkbox)");
    }
}

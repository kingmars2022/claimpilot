package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.junit.jupiter.api.Test;

import com.claimpilot.samples.SampleDocuments;

class PdfFormFillerTest {

    @Test
    void writesValuesAndNeverTouchesSignatureFields() throws Exception {
        FormTemplate form = FormTemplate.of("test", SampleDocuments.claimForm());
        Map<String, DataKey> mapping = new LinkedHashMap<>();
        mapping.put("txtField_01", DataKey.MEMBER_NAME);
        mapping.put("txtField_17", DataKey.AMOUNT_CLAIMED);
        mapping.put("sigField_01", DataKey.SIGNATURE);
        mapping.put("chkField_01", DataKey.DECLARATION);

        byte[] filled = PdfFormFiller.fill(form, mapping, Map.of(
                DataKey.MEMBER_NAME, "Marc Gagnon",
                DataKey.AMOUNT_CLAIMED, "36.00",
                DataKey.SIGNATURE, "Marc Gagnon",
                DataKey.DECLARATION, "Yes"));

        try (PDDocument pdf = Loader.loadPDF(filled)) {
            PDAcroForm acro = pdf.getDocumentCatalog().getAcroForm();
            assertThat(acro.getField("txtField_01").getValueAsString()).isEqualTo("Marc Gagnon");
            assertThat(acro.getField("txtField_17").getValueAsString()).isEqualTo("36.00");
            assertThat(acro.getField("sigField_01").getValueAsString()).isEmpty();
            assertThat(((PDCheckBox) acro.getField("chkField_01")).isChecked()).isFalse();
            assertThat(acro.getField("txtField_05").getValueAsString()).isEmpty();
        }
    }
}

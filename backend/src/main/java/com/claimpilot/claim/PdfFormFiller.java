package com.claimpilot.claim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/**
 * Writes values into a PDF form. This is plain code, not AI: given the same values it always
 * produces the same form, which is what makes it testable. The form stays editable so the member
 * can correct it and sign.
 */
public final class PdfFormFiller {

    private PdfFormFiller() {
    }

    /**
     * @param mapping PDF field name to data key
     * @param values  data key to the value to write; keys that must never be filled are ignored
     */
    public static byte[] fill(FormTemplate template, Map<String, DataKey> mapping, Map<DataKey, String> values) {
        try (PDDocument pdf = Loader.loadPDF(template.bytes())) {
            PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
            for (Map.Entry<String, DataKey> entry : mapping.entrySet()) {
                DataKey key = entry.getValue();
                String value = values.get(key);
                if (key.neverFill() || value == null || value.isBlank()) {
                    continue;
                }
                PDField field = form.getField(entry.getKey());
                if (field instanceof PDTextField text) {
                    text.setValue(value);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not fill the form", ex);
        }
    }
}

package com.claimpilot.claim;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.core.io.ClassPathResource;

/**
 * A fillable PDF claim form. Its SHA-256 identifies the exact version, so a field mapping worked
 * out for one version is never applied to a changed form.
 */
public final class FormTemplate {

    /** The fictional Cedarview Assurance second-plan claim form used in the demo. */
    public static final String SECONDARY_CLAIM_FORM = "forms/cedarview-secondary-claim-form.pdf";

    private final String name;
    private final byte[] bytes;
    private final String sha256;
    private final List<Field> fields;

    /**
     * @param label the field's tooltip, which is what a person reading the form sees next to it
     */
    public record Field(String name, String label, boolean checkbox) {
    }

    private FormTemplate(String name, byte[] bytes) {
        this.name = name;
        this.bytes = bytes;
        this.sha256 = sha256(bytes);
        this.fields = readFields(bytes);
    }

    public static FormTemplate classpath(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new FormTemplate(path, in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException("Form template " + path + " is missing", ex);
        }
    }

    public static FormTemplate of(String name, byte[] bytes) {
        return new FormTemplate(name, bytes);
    }

    public String name() {
        return name;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String sha256() {
        return sha256;
    }

    public List<Field> fields() {
        return fields;
    }

    private static List<Field> readFields(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
            List<Field> result = new ArrayList<>();
            if (form == null) {
                return result;
            }
            for (PDField field : form.getFieldTree()) {
                if (field.getWidgets().isEmpty()) {
                    continue;  // a parent node, not a field on the page
                }
                String label = field.getAlternateFieldName() == null ? "" : field.getAlternateFieldName();
                result.add(new Field(field.getFullyQualifiedName(), label, field instanceof PDCheckBox));
            }
            return result;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the form fields", ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

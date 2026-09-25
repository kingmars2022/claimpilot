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
    /** The fictional Harbourline Vie claim form, in French. */
    public static final String FRENCH_CLAIM_FORM = "forms/harbourline-demande-de-remboursement.pdf";

    private final String name;
    private final byte[] bytes;
    private final String sha256;
    private final List<Field> fields;
    /** For a form shipped with the app: its reviewed field mapping (JSON), so no model call is needed. */
    private final String presetMapping;

    /**
     * @param label the field's tooltip, which is what a person reading the form sees next to it
     */
    public record Field(String name, String label, boolean checkbox) {
    }

    private FormTemplate(String name, byte[] bytes, String presetMapping) {
        this.name = name;
        this.bytes = bytes;
        this.sha256 = sha256(bytes);
        this.fields = readFields(bytes);
        this.presetMapping = presetMapping;
    }

    /**
     * A form shipped with the app, with its reviewed mapping next to it
     * ({@code forms/x.pdf} and {@code forms/x.mapping.json}).
     */
    public static FormTemplate builtIn(String path) {
        String mapping = new String(read(path.replaceFirst("\\.pdf$", ".mapping.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        return new FormTemplate(path, read(path), mapping);
    }

    public static FormTemplate of(String name, byte[] bytes) {
        return new FormTemplate(name, bytes, null);
    }

    private static byte[] read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Form resource " + path + " is missing", ex);
        }
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

    /** The reviewed mapping of a built-in form as JSON, or empty for a form the user uploaded. */
    public java.util.Optional<String> presetMapping() {
        return java.util.Optional.ofNullable(presetMapping);
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

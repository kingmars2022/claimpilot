package com.claimpilot.claim;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "form_field_mappings")
public class FormFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_sha256", nullable = false)
    private String templateSha256;

    @Column(nullable = false)
    private String pdfFieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataKey dataKey;

    private String label;

    @Column(nullable = false)
    private Instant createdAt;

    protected FormFieldMapping() {
        // for JPA
    }

    public FormFieldMapping(String templateSha256, String pdfFieldName, DataKey dataKey, String label) {
        this.templateSha256 = templateSha256;
        this.pdfFieldName = pdfFieldName;
        this.dataKey = dataKey;
        this.label = label;
        this.createdAt = Instant.now();
    }

    public String getPdfFieldName() {
        return pdfFieldName;
    }

    public DataKey getDataKey() {
        return dataKey;
    }
}

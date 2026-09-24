package com.claimpilot.claim;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.claimpilot.common.NotFoundException;
import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentRepository;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.document.UploadedDocument;
import com.claimpilot.storage.FileStorage;
import com.claimpilot.user.AppUser;

/**
 * The claim forms a claim can be filled on: forms shipped with the app, and fillable PDFs the user
 * uploaded (their insurer's own form). A form is referred to by a key, {@code builtin:<name>} or
 * {@code upload:<document id>}, which is stored on each claim.
 */
@Service
public class FormCatalog {

    public static final String DEFAULT_KEY = "builtin:cedarview-secondary";
    private static final String BUILTIN = "builtin:";
    private static final String UPLOAD = "upload:";

    /** A form the user can choose. */
    public record FormOption(String key, String name, boolean builtIn, DocumentStatus status, Integer fieldCount,
                             String errorMessage, Instant createdAt) {
    }

    private record BuiltIn(String name, FormTemplate template) {
    }

    private final Map<String, BuiltIn> builtIns = new LinkedHashMap<>();
    private final DocumentRepository documents;
    private final FileStorage storage;
    /** Uploaded forms by storage key; a stored file never changes, so this never goes stale. */
    private final Map<String, FormTemplate> uploaded = new ConcurrentHashMap<>();

    public FormCatalog(DocumentRepository documents, FileStorage storage) {
        this.documents = documents;
        this.storage = storage;
        builtIns.put(DEFAULT_KEY, new BuiltIn("Cedarview Assurance: Supplementary Health Claim (English)",
                FormTemplate.classpath(FormTemplate.SECONDARY_CLAIM_FORM)));
        builtIns.put("builtin:harbourline-demande", new BuiltIn(
                "Harbourline Vie: Demande de remboursement (French)",
                FormTemplate.classpath(FormTemplate.FRENCH_CLAIM_FORM)));
    }

    public List<FormOption> list(AppUser user) {
        List<FormOption> options = new ArrayList<>();
        builtIns.forEach((key, form) -> options.add(new FormOption(key, form.name(), true, DocumentStatus.READY,
                form.template().fields().size(), null, null)));
        documents.findByOwnerIdAndKindOrderByCreatedAtDesc(user.getId(), DocumentKind.FORM)
                .forEach(doc -> options.add(new FormOption(UPLOAD + doc.getId(), doc.getFileName(), false,
                        doc.getStatus(), doc.getChunkCount(), doc.getErrorMessage(), doc.getCreatedAt())));
        return options;
    }

    /** The form behind a key, checking an uploaded form belongs to this user and is ready. */
    public FormTemplate resolve(AppUser user, String key) {
        String formKey = key == null || key.isBlank() ? DEFAULT_KEY : key;
        if (formKey.startsWith(BUILTIN)) {
            BuiltIn builtIn = builtIns.get(formKey);
            if (builtIn == null) {
                throw new NotFoundException("Claim form " + formKey + " does not exist.");
            }
            return builtIn.template();
        }
        UploadedDocument doc = uploadedForm(user, formKey);
        if (doc.getStatus() != DocumentStatus.READY) {
            throw new IllegalArgumentException(doc.getFileName() + " is not ready as a claim form.");
        }
        return uploaded.computeIfAbsent(doc.getStorageKey(), storageKey -> read(doc));
    }

    /** A short, human name for the form behind a key. */
    public String name(AppUser user, String key) {
        String formKey = key == null || key.isBlank() ? DEFAULT_KEY : key;
        BuiltIn builtIn = builtIns.get(formKey);
        if (builtIn != null) {
            return builtIn.name();
        }
        try {
            return uploadedForm(user, formKey).getFileName();
        } catch (NotFoundException ex) {
            return "A deleted claim form";
        }
    }

    private UploadedDocument uploadedForm(AppUser user, String key) {
        if (!key.startsWith(UPLOAD)) {
            throw new NotFoundException("Claim form " + key + " does not exist.");
        }
        UUID id;
        try {
            id = UUID.fromString(key.substring(UPLOAD.length()));
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Claim form " + key + " does not exist.");
        }
        return documents.findByIdAndOwnerId(id, user.getId())
                .filter(d -> d.getKind() == DocumentKind.FORM)
                .orElseThrow(() -> new NotFoundException("Claim form " + key + " does not exist."));
    }

    private FormTemplate read(UploadedDocument doc) {
        try (InputStream in = storage.load(doc.getStorageKey()).getInputStream()) {
            return FormTemplate.of(doc.getFileName(), in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

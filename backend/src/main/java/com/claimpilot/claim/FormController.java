package com.claimpilot.claim;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentResponse;
import com.claimpilot.document.DocumentService;
import com.claimpilot.user.CurrentUserService;

/** Claim forms: the built-in ones and the user's own fillable PDFs from their insurer. */
@RestController
@RequestMapping("/api/forms")
public class FormController {

    private final FormCatalog catalog;
    private final DocumentService documents;
    private final CurrentUserService currentUser;

    public FormController(FormCatalog catalog, DocumentService documents, CurrentUserService currentUser) {
        this.catalog = catalog;
        this.documents = documents;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<FormCatalog.FormOption> list() {
        return catalog.list(currentUser.get());
    }

    /** Returns 202 Accepted: the form's fields are read in the background. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.accepted().body(documents.upload(currentUser.get(), DocumentKind.FORM, file));
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documents.get(currentUser.get(), DocumentKind.FORM, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(currentUser.get(), DocumentKind.FORM, id);
        return ResponseEntity.noContent().build();
    }
}

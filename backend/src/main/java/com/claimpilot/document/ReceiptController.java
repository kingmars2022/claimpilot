package com.claimpilot.document;

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

import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final DocumentService service;
    private final CurrentUserService currentUser;

    public ReceiptController(DocumentService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Returns 202 Accepted: the file is stored, text recognition and extraction continue in the background. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.accepted().body(service.upload(currentUser.get(), DocumentKind.RECEIPT, file));
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return service.list(currentUser.get(), DocumentKind.RECEIPT);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return service.get(currentUser.get(), DocumentKind.RECEIPT, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.get(), DocumentKind.RECEIPT, id);
        return ResponseEntity.noContent().build();
    }
}

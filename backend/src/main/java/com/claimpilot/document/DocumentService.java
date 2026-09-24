package com.claimpilot.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.claimpilot.audit.AuditAction;
import com.claimpilot.audit.AuditService;
import com.claimpilot.cache.ModelCache;
import com.claimpilot.common.NotFoundException;
import com.claimpilot.conversation.ConversationRepository;
import com.claimpilot.events.ProcessingDispatcher;
import com.claimpilot.extraction.DocumentFactRepository;
import com.claimpilot.extraction.ExtractionLogRepository;
import com.claimpilot.storage.FileStorage;
import com.claimpilot.user.AppUser;

/** Policies, receipts and claim forms. Every method takes the signed-in user and only touches their files. */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final ProcessingDispatcher dispatcher;
    private final AuditService audit;
    private final VectorStore vectorStore;
    private final DocumentFactRepository facts;
    private final ExtractionLogRepository extractionLogs;
    private final ConversationRepository conversations;
    private final ModelCache modelCache;

    public DocumentService(DocumentRepository repository, FileStorage storage, ProcessingDispatcher dispatcher,
                           AuditService audit,
                           VectorStore vectorStore, DocumentFactRepository facts,
                           ExtractionLogRepository extractionLogs, ConversationRepository conversations,
                           ModelCache modelCache) {
        this.modelCache = modelCache;
        this.repository = repository;
        this.storage = storage;
        this.dispatcher = dispatcher;
        this.audit = audit;
        this.vectorStore = vectorStore;
        this.facts = facts;
        this.extractionLogs = extractionLogs;
        this.conversations = conversations;
    }

    /**
     * Saves the file and schedules processing. Deliberately not @Transactional: the row must be
     * committed before the background job looks it up.
     */
    public DocumentResponse upload(AppUser owner, DocumentKind kind, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty.");
        }
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = DocumentReaderFactory.extensionOf(fileName);
        if (!kind.extensions().contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type. Upload one of: "
                    + kind.extensions().stream().sorted().toList());
        }
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(fileName, in);
        }
        UploadedDocument saved = repository.save(
                new UploadedDocument(owner.getId(), kind, fileName, file.getContentType(), file.getSize(), key));
        audit.record(owner.getId(), AuditAction.DOCUMENT_UPLOADED, kind.name(), saved.getId(), fileName);
        dispatcher.documentUploaded(saved.getId());
        return DocumentResponse.from(saved, List.of());
    }

    public List<DocumentResponse> list(AppUser owner, DocumentKind kind) {
        return repository.findByOwnerIdAndKindOrderByCreatedAtDesc(owner.getId(), kind).stream()
                .map(d -> DocumentResponse.from(d, facts.findByDocumentId(d.getId())))
                .toList();
    }

    public DocumentResponse get(AppUser owner, DocumentKind kind, UUID id) {
        UploadedDocument doc = find(owner, kind, id);
        return DocumentResponse.from(doc, facts.findByDocumentId(id));
    }

    /**
     * The document if it belongs to this user and has this kind. Another user's document is
     * reported as missing rather than forbidden, so ids cannot be probed.
     */
    public UploadedDocument find(AppUser owner, DocumentKind kind, UUID id) {
        return repository.findByIdAndOwnerId(id, owner.getId())
                .filter(d -> d.getKind() == kind)
                .orElseThrow(() -> new NotFoundException(label(kind) + " " + id + " does not exist."));
    }

    public java.util.Optional<String> fileNameOf(UUID id) {
        return repository.findById(id).map(UploadedDocument::getFileName);
    }

    /**
     * Removes the vectors, the facts, the extraction logs, the stored file and the row. Deleting a
     * policy also deletes the questions asked about it, since they can no longer be continued.
     */
    public void delete(AppUser owner, DocumentKind kind, UUID id) {
        UploadedDocument doc = find(owner, kind, id);
        remove(doc);
        audit.record(owner.getId(), AuditAction.DOCUMENT_DELETED, kind.name(), id, doc.getFileName());
        if (kind == DocumentKind.POLICY) {
            modelCache.evictOwner(owner.getId());  // cached replies may quote this policy
            conversations.deleteByOwnerAndPolicyId(owner.getUsername(), id);
        }
    }

    /** Removes every file of this user (used when the account is deleted). */
    public void deleteAll(AppUser owner) {
        modelCache.evictOwner(owner.getId());
        repository.findByOwnerId(owner.getId()).forEach(this::remove);
        vectorStore.delete(OwnerScope.owner(owner.getId()));
    }

    private void remove(UploadedDocument doc) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(ProcessingService.META_DOCUMENT_ID, doc.getId().toString())
                .build());
        facts.deleteByDocumentId(doc.getId());
        extractionLogs.deleteByDocumentId(doc.getId());
        try {
            storage.delete(doc.getStorageKey());
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", doc.getStorageKey(), ex);
        }
        repository.delete(doc);
    }

    private static String label(DocumentKind kind) {
        return switch (kind) {
            case POLICY -> "Policy";
            case RECEIPT -> "Receipt";
            case FORM -> "Claim form";
        };
    }
}

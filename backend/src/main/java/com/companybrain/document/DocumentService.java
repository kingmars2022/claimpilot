package com.companybrain.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.companybrain.common.NotFoundException;
import com.companybrain.storage.FileStorage;
import com.companybrain.user.Department;
import com.companybrain.user.DepartmentRepository;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final IndexingService indexingService;
    private final VectorStore vectorStore;
    private final DepartmentRepository departments;
    private final DocumentAccess access;

    public DocumentService(DocumentRepository repository, FileStorage storage, IndexingService indexingService,
                           VectorStore vectorStore, DepartmentRepository departments, DocumentAccess access) {
        this.repository = repository;
        this.storage = storage;
        this.indexingService = indexingService;
        this.vectorStore = vectorStore;
        this.departments = departments;
        this.access = access;
    }

    /**
     * Saves the file and schedules indexing. Deliberately not @Transactional: the row must be
     * committed before the background job looks it up.
     *
     * @param departmentIds departments that may see the document; empty for the whole company
     */
    public DocumentResponse upload(MultipartFile file, Collection<Long> departmentIds) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty.");
        }
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = DocumentReaderFactory.extensionOf(fileName);
        if (!DocumentReaderFactory.SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Upload one of: " + DocumentReaderFactory.SUPPORTED_EXTENSIONS);
        }

        Set<Department> visibleTo = departments(departmentIds);
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(fileName, in);
        }
        KnowledgeDocument saved = repository.save(
                new KnowledgeDocument(fileName, file.getContentType(), file.getSize(), key, visibleTo));
        indexingService.indexAsync(saved.getId());
        return DocumentResponse.from(saved);
    }

    public List<DocumentResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(DocumentResponse::from).toList();
    }

    public DocumentResponse get(UUID id) {
        return DocumentResponse.from(find(id));
    }

    /**
     * Changes who can see a document. The database row and the access list on every indexed chunk
     * are updated in one transaction, so retrieval never sees a half-applied change.
     */
    @Transactional
    public DocumentResponse updateVisibility(UUID id, Collection<Long> departmentIds) {
        KnowledgeDocument doc = find(id);
        doc.restrictTo(departments(departmentIds));
        repository.saveAndFlush(doc);
        int chunks = access.applyToVectors(doc);
        log.info("Visibility of {} set to {} ({} chunks updated)", doc.getFileName(),
                DocumentAccess.accessList(doc), chunks);
        return DocumentResponse.from(doc);
    }

    /** Removes the vectors, the stored file and the database row. */
    public void delete(UUID id) {
        KnowledgeDocument doc = find(id);
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(IndexingService.META_DOCUMENT_ID, id.toString())
                .build());
        try {
            storage.delete(doc.getStorageKey());
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", doc.getStorageKey(), ex);
        }
        repository.delete(doc);
    }

    private Set<Department> departments(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> unique = new HashSet<>(ids);
        List<Department> found = departments.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new IllegalArgumentException("One or more departments do not exist.");
        }
        return new HashSet<>(found);
    }

    private KnowledgeDocument find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document " + id + " does not exist."));
    }
}

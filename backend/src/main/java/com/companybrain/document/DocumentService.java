package com.companybrain.document;

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

import com.companybrain.common.NotFoundException;
import com.companybrain.storage.FileStorage;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final IndexingService indexingService;
    private final VectorStore vectorStore;

    public DocumentService(DocumentRepository repository, FileStorage storage,
                           IndexingService indexingService, VectorStore vectorStore) {
        this.repository = repository;
        this.storage = storage;
        this.indexingService = indexingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Saves the file and schedules indexing. Deliberately not @Transactional: the row must be
     * committed before the background job looks it up.
     */
    public DocumentResponse upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty.");
        }
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = DocumentReaderFactory.extensionOf(fileName);
        if (!DocumentReaderFactory.SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Upload one of: " + DocumentReaderFactory.SUPPORTED_EXTENSIONS);
        }

        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(fileName, in);
        }
        KnowledgeDocument saved = repository.save(
                new KnowledgeDocument(fileName, file.getContentType(), file.getSize(), key));
        indexingService.indexAsync(saved.getId());
        return DocumentResponse.from(saved);
    }

    public List<DocumentResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(DocumentResponse::from).toList();
    }

    public DocumentResponse get(UUID id) {
        return DocumentResponse.from(find(id));
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

    private KnowledgeDocument find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document " + id + " does not exist."));
    }
}

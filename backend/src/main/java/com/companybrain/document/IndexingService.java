package com.companybrain.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.companybrain.config.AppProperties;
import com.companybrain.storage.FileStorage;

/**
 * Turns an uploaded file into searchable chunks: extract text, split, embed, store in pgvector.
 * Runs in the background so the upload request returns immediately (HTTP 202).
 */
@Service
public class IndexingService {

    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_FILE_NAME = "fileName";
    public static final String META_PAGE = "page";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_SECTION = MarkdownSections.META_SECTION;

    private static final String PDF_PAGE_KEY = "page_number";
    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final DocumentReaderFactory readerFactory;
    private final VectorStore vectorStore;
    private final DocumentAccess access;
    private final TokenTextSplitter splitter;

    public IndexingService(DocumentRepository repository, FileStorage storage,
                           DocumentReaderFactory readerFactory, VectorStore vectorStore,
                           DocumentAccess access, AppProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.readerFactory = readerFactory;
        this.vectorStore = vectorStore;
        this.access = access;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.indexing().chunkSize())
                .withMinChunkSizeChars(100)
                .build();
    }

    @Async
    public void indexAsync(UUID documentId) {
        KnowledgeDocument doc = repository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("Document {} disappeared before indexing", documentId);
            return;
        }
        doc.markProcessing();
        repository.save(doc);

        int chunkCount;
        try {
            List<Document> pages = readerFactory.read(storage.load(doc.getStorageKey()), doc.getFileName());
            List<Document> chunks = toChunks(splitter.apply(pages), doc);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No readable text found. Scanned PDFs need OCR first.");
            }
            vectorStore.add(chunks);
            chunkCount = chunks.size();
        } catch (Exception ex) {
            log.error("Indexing failed for {}", doc.getFileName(), ex);
            repository.findById(documentId).ifPresent(fresh -> {
                fresh.markFailed(ex.getMessage());
                repository.save(fresh);
            });
            return;
        }
        finish(documentId, chunkCount);
    }

    /**
     * Records the result on a freshly loaded row. Saving the copy loaded before indexing would
     * overwrite a visibility change made in the meantime.
     */
    private void finish(UUID documentId, int chunkCount) {
        KnowledgeDocument fresh = repository.findById(documentId).orElse(null);
        if (fresh == null) {
            // Deleted while indexing: remove the chunks just added so nothing is left orphaned.
            vectorStore.delete(new FilterExpressionBuilder().eq(META_DOCUMENT_ID, documentId.toString()).build());
            return;
        }
        access.applyToVectors(fresh);  // picks up a visibility change made during indexing
        fresh.markIndexed(chunkCount);
        repository.save(fresh);
        log.info("Indexed {} ({} chunks)", fresh.getFileName(), chunkCount);
    }

    /** Copies each chunk with the metadata needed for citations and deletion. */
    private List<Document> toChunks(List<Document> split, KnowledgeDocument doc) {
        List<Document> result = new ArrayList<>();
        int index = 0;
        for (Document chunk : split) {
            String text = chunk.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_DOCUMENT_ID, doc.getId().toString());
            metadata.put(META_FILE_NAME, doc.getFileName());
            metadata.put(META_CHUNK_INDEX, index++);
            metadata.put(DocumentAccess.META_ACCESS, DocumentAccess.accessList(doc));
            Object page = chunk.getMetadata().get(PDF_PAGE_KEY);
            if (page != null) {
                metadata.put(META_PAGE, page);
            }
            Object section = chunk.getMetadata().get(META_SECTION);
            if (section != null) {
                metadata.put(META_SECTION, section);
            }
            result.add(new Document(text, metadata));
        }
        return result;
    }
}

package com.claimpilot.document;

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

import com.claimpilot.claim.FormTemplate;
import com.claimpilot.config.AppProperties;
import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.DocumentFactRepository;
import com.claimpilot.extraction.ExtractedFact;
import com.claimpilot.extraction.FactExtractor;
import com.claimpilot.storage.FileStorage;

/**
 * Runs in the background after an upload (the request returns 202 at once):
 * a policy is split into chunks, embedded and stored in pgvector, and read for its key facts;
 * a receipt is read for its key facts only; a claim form is checked for fillable fields.
 */
@Service
public class ProcessingService {

    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_FILE_NAME = "fileName";
    public static final String META_PAGE = "page";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_SECTION = MarkdownSections.META_SECTION;

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final DocumentReaderFactory readerFactory;
    private final VectorStore vectorStore;
    private final FactExtractor factExtractor;
    private final DocumentFactRepository facts;
    private final TokenTextSplitter splitter;

    public ProcessingService(DocumentRepository repository, FileStorage storage, DocumentReaderFactory readerFactory,
                             VectorStore vectorStore, FactExtractor factExtractor, DocumentFactRepository facts,
                             AppProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.readerFactory = readerFactory;
        this.vectorStore = vectorStore;
        this.factExtractor = factExtractor;
        this.facts = facts;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.indexing().chunkSize())
                .withMinChunkSizeChars(100)
                .build();
    }

    @Async
    public void processAsync(UUID documentId) {
        UploadedDocument doc = repository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("Document {} disappeared before processing", documentId);
            return;
        }
        doc.markProcessing();
        repository.save(doc);

        Integer chunkCount = null;
        try {
            if (doc.getKind() == DocumentKind.FORM) {
                chunkCount = countFormFields(doc);
                markReady(documentId, chunkCount);
                return;
            }
            DocumentReaderFactory.ReadResult read = readerFactory.read(storage.load(doc.getStorageKey()), doc.getFileName());
            if (read.pages().stream().allMatch(p -> p.text().isBlank())) {
                throw new IllegalStateException("No readable text found. Try a clearer photo or a text PDF.");
            }
            if (doc.getKind() == DocumentKind.POLICY) {
                List<Document> chunks = toChunks(splitter.apply(read.units()), doc);
                vectorStore.add(chunks);
                chunkCount = chunks.size();
            }
            List<ExtractedFact> extracted = factExtractor.extract(doc, read.pages());
            facts.deleteByDocumentId(documentId);
            facts.saveAll(extracted.stream().map(f -> new DocumentFact(documentId, f)).toList());
        } catch (Exception ex) {
            log.error("Processing failed for {}", doc.getFileName(), ex);
            repository.findById(documentId).ifPresent(fresh -> {
                fresh.markFailed(ex.getMessage());
                repository.save(fresh);
            });
            return;
        }

        markReady(documentId, chunkCount);
    }

    private void markReady(UUID documentId, Integer count) {
        UploadedDocument fresh = repository.findById(documentId).orElse(null);
        if (fresh == null) {
            // Deleted while processing: remove the chunks just added so nothing is left behind.
            vectorStore.delete(new FilterExpressionBuilder().eq(META_DOCUMENT_ID, documentId.toString()).build());
            facts.deleteByDocumentId(documentId);
            return;
        }
        fresh.markReady(count);
        repository.save(fresh);
        log.info("Processed {} {}", fresh.getKind(), fresh.getFileName());
    }

    /** A claim form must be a fillable PDF; its field count is stored in place of a chunk count. */
    private int countFormFields(UploadedDocument doc) throws java.io.IOException {
        byte[] bytes;
        try (java.io.InputStream in = storage.load(doc.getStorageKey()).getInputStream()) {
            bytes = in.readAllBytes();
        }
        int fields = FormTemplate.of(doc.getFileName(), bytes).fields().size();
        if (fields == 0) {
            throw new IllegalStateException(
                    "This PDF has no fillable fields. Upload the insurer's fillable (interactive) PDF form.");
        }
        return fields;
    }

    /** Copies each chunk with the metadata needed for citations, isolation and deletion. */
    private List<Document> toChunks(List<Document> split, UploadedDocument doc) {
        List<Document> result = new ArrayList<>();
        int index = 0;
        for (Document chunk : split) {
            String text = chunk.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_DOCUMENT_ID, doc.getId().toString());
            metadata.put(OwnerScope.META_OWNER_ID, doc.getOwnerId().toString());
            metadata.put(META_FILE_NAME, doc.getFileName());
            metadata.put(META_CHUNK_INDEX, index++);
            Object page = chunk.getMetadata().get(DocumentReaderFactory.PDF_PAGE_KEY);
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

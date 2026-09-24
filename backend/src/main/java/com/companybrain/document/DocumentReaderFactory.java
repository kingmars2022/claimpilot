package com.companybrain.document;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Picks a text extractor by file type. PDFs are read page by page so answers can cite page numbers.
 */
@Component
public class DocumentReaderFactory {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

    public List<Document> read(Resource resource, String fileName) {
        if ("pdf".equals(extensionOf(fileName))) {
            return new PagePdfDocumentReader(resource).get();
        }
        return new TikaDocumentReader(resource).get();
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

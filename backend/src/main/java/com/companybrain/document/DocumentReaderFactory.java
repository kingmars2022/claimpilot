package com.companybrain.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Picks a text extractor by file type. PDFs are read page by page so answers can cite page numbers;
 * Markdown and text files are split at their headings so answers can cite the section.
 */
@Component
public class DocumentReaderFactory {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

    public List<Document> read(Resource resource, String fileName) {
        return switch (extensionOf(fileName)) {
            case "pdf" -> new PagePdfDocumentReader(resource).get();
            case "md", "txt" -> MarkdownSections.split(readUtf8(resource));
            default -> new TikaDocumentReader(resource).get();
        };
    }

    private static String readUtf8(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

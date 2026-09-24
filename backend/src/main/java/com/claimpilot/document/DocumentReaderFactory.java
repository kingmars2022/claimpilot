package com.claimpilot.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.claimpilot.extraction.OcrService;
import com.claimpilot.extraction.PageText;

/**
 * Extracts text by file type. PDFs are read page by page so answers and filled form fields can
 * cite page numbers, with OCR for scanned pages; Markdown and text are split at headings; photos
 * go through OCR.
 */
@Component
public class DocumentReaderFactory {

    static final String PDF_PAGE_KEY = "page_number";
    static final String META_OCR = "ocr";

    /** Fewer characters than this on a PDF page means it is a scanned image. */
    static final int MIN_TEXT_PER_PAGE = 25;
    /** Rendering resolution for OCR; 300 dpi is what Tesseract recommends. */
    private static final int OCR_DPI = 300;
    /** OCR takes a few seconds per page, so very long scans are cut off. */
    private static final int MAX_OCR_PAGES = 40;

    private static final Logger log = LoggerFactory.getLogger(DocumentReaderFactory.class);
    private static final List<String> IMAGES = List.of("png", "jpg", "jpeg");

    private final OcrService ocr;

    public DocumentReaderFactory(OcrService ocr) {
        this.ocr = ocr;
    }

    /**
     * @param pages page texts, used to extract and verify facts
     * @param units the pieces to split into search chunks, with page or section metadata
     */
    public record ReadResult(List<PageText> pages, List<Document> units) {
    }

    public ReadResult read(Resource resource, String fileName) throws IOException {
        String extension = extensionOf(fileName);
        if ("pdf".equals(extension)) {
            return readPdf(resource);
        }
        if (IMAGES.contains(extension)) {
            String text = ocr.read(resource, extension);
            return new ReadResult(List.of(new PageText(1, text)),
                    List.of(new Document(text, Map.of(PDF_PAGE_KEY, 1))));
        }
        if ("md".equals(extension) || "txt".equals(extension)) {
            String text = readUtf8(resource);
            return new ReadResult(List.of(new PageText(null, text)), MarkdownSections.split(text));
        }
        List<Document> docs = new TikaDocumentReader(resource).get();
        String text = docs.stream().map(Document::getText).filter(t -> t != null).reduce("", (a, b) -> a + "\n" + b);
        return new ReadResult(List.of(new PageText(null, text)), docs);
    }

    /**
     * Reads a PDF page by page. A page with (almost) no text layer is a scan: it is rendered as an
     * image and read with OCR, so scanned and photographed policies work like digital ones.
     */
    private ReadResult readPdf(Resource resource) throws IOException {
        List<PageText> pages = new ArrayList<>();
        List<Document> units = new ArrayList<>();
        try (InputStream in = resource.getInputStream(); PDDocument pdf = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdf);
            int ocrPages = 0;
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                int pageNumber = i + 1;
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = stripper.getText(pdf);
                boolean scanned = text.strip().length() < MIN_TEXT_PER_PAGE;
                if (scanned && ocrPages < MAX_OCR_PAGES) {
                    BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.GRAY);
                    text = ocr.read(image);
                    ocrPages++;
                }
                pages.add(new PageText(pageNumber, text));
                if (!text.isBlank()) {
                    units.add(new Document(text, Map.of(PDF_PAGE_KEY, pageNumber, META_OCR, scanned)));
                }
            }
            if (ocrPages > 0) {
                log.info("Read {} scanned page(s) with OCR", ocrPages);
            }
        }
        return new ReadResult(pages, units);
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String readUtf8(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

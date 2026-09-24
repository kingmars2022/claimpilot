package com.claimpilot.extraction;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.claimpilot.config.AppProperties;

/**
 * Reads the text of a photo, or of a scanned PDF page, with the Tesseract command-line tool
 * (free, local, fast). On macOS: {@code brew install tesseract}; add {@code tesseract-lang} for French.
 */
@Service
public class OcrService {

    private static final long TIMEOUT_SECONDS = 60;

    private final AppProperties.Ocr settings;

    public OcrService(AppProperties properties) {
        this.settings = properties.ocr();
    }

    /** Text of an uploaded photo (PNG or JPEG). */
    public String read(Resource image, String extension) throws IOException {
        Path input = Files.createTempFile("claimpilot-ocr-", "." + extension);
        try {
            try (InputStream in = image.getInputStream()) {
                Files.copy(in, input, StandardCopyOption.REPLACE_EXISTING);
            }
            return run(input);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    /** Text of a rendered page, for example a scanned page inside a PDF. */
    public String read(BufferedImage page) throws IOException {
        Path input = Files.createTempFile("claimpilot-ocr-", ".png");
        try {
            ImageIO.write(page, "png", input.toFile());
            return run(input);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private String run(Path input) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(List.of(settings.command(), input.toString(), "stdout",
                    "-l", settings.languages(), "--psm", "4"))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ex) {
            throw new IOException("Tesseract is not installed or not on the PATH. On macOS run: brew install tesseract", ex);
        }
        try {
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Text recognition took longer than " + TIMEOUT_SECONDS + " seconds.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Text recognition failed (tesseract exit code " + process.exitValue() + ").");
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Text recognition was interrupted.", ex);
        }
    }
}

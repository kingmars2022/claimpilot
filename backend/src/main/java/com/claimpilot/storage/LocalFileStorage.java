package com.claimpilot.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** Files on the local disk, under {@code claimpilot.storage.local-root}. */
public class LocalFileStorage implements FileStorage {

    private static final int MAX_NAME_LENGTH = 150;

    private final Path root;

    public LocalFileStorage(String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(String originalFileName, InputStream content) throws IOException {
        String key = UUID.randomUUID() + "/" + sanitize(originalFileName);
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target);
        return key;
    }

    @Override
    public Resource load(String key) {
        return new FileSystemResource(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Path file = resolve(key);
        Files.deleteIfExists(file);
        Path folder = file.getParent();
        if (folder != null && !folder.equals(root)) {
            Files.deleteIfExists(folder);
        }
    }

    /** Resolves a key under the storage root and rejects path traversal such as "../". */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    @Override
    public String toString() {
        return "folder " + root;
    }

    /** Keeps letters in any script (é, 中) and digits; replaces everything else. */
    static String sanitize(String name) {
        String base = (name == null || name.isBlank()) ? "document" : name.strip();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(cleaned.length() - MAX_NAME_LENGTH) : cleaned;
    }
}

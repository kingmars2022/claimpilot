package com.claimpilot.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedFileStorageTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] RECEIPT = "Total charged: $120.00".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    @Test
    void filesAreEncryptedOnDiskAndReadBackAsTheOriginal() throws Exception {
        EncryptedFileStorage storage = new EncryptedFileStorage(new LocalFileStorage(root.toString()), KEY);

        String key = storage.store("receipt.txt", new ByteArrayInputStream(RECEIPT));

        byte[] onDisk = Files.readAllBytes(root.resolve(key));
        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).startsWith("CPE1").doesNotContain("120.00");
        try (InputStream in = storage.load(key).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(RECEIPT);
        }
        assertThat(storage.load(key).getFilename()).isEqualTo("receipt.txt");
    }

    @Test
    void filesStoredBeforeEncryptionAreStillReadable() throws Exception {
        LocalFileStorage plain = new LocalFileStorage(root.toString());
        String key = plain.store("old.txt", new ByteArrayInputStream(RECEIPT));

        EncryptedFileStorage storage = new EncryptedFileStorage(plain, KEY);

        try (InputStream in = storage.load(key).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(RECEIPT);
        }
    }

    @Test
    void aChangedFileIsRejected() throws Exception {
        EncryptedFileStorage storage = new EncryptedFileStorage(new LocalFileStorage(root.toString()), KEY);
        String key = storage.store("receipt.txt", new ByteArrayInputStream(RECEIPT));
        byte[] onDisk = Files.readAllBytes(root.resolve(key));
        onDisk[onDisk.length - 1] ^= 1;
        Files.write(root.resolve(key), onDisk);

        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void theKeyMustBe32Bytes() {
        assertThatThrownBy(() -> new EncryptedFileStorage(new LocalFileStorage(root.toString()), "c2hvcnQ="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

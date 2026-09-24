package com.claimpilot.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Encrypts every stored file with AES-256-GCM before handing it to the underlying storage, so a
 * copied disk or a leaked bucket reveals nothing. Each file gets a fresh random IV; GCM also
 * detects any change to the stored bytes.
 *
 * <pre>
 * stored file = "CPE1" | 12-byte IV | ciphertext with 16-byte tag
 * </pre>
 *
 * Files written before encryption was turned on have no "CPE1" header and are returned as they are.
 */
public class EncryptedFileStorage implements FileStorage {

    static final byte[] MAGIC = "CPE1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final FileStorage delegate;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedFileStorage(FileStorage delegate, String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != 32) {
            throw new IllegalArgumentException("claimpilot.storage.encryption-key must be 32 bytes, base64-encoded");
        }
        this.delegate = delegate;
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String store(String originalFileName, InputStream content) throws IOException {
        return delegate.store(originalFileName, new ByteArrayInputStream(encrypt(content.readAllBytes())));
    }

    @Override
    public Resource load(String key) {
        Resource stored = delegate.load(key);
        byte[] bytes;
        try (InputStream in = stored.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new StorageException("Could not read " + key, ex);
        }
        byte[] plain = startsWithMagic(bytes) ? decrypt(bytes) : bytes;
        String fileName = stored.getFilename();
        return new ByteArrayResource(plain) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    @Override
    public void delete(String key) throws IOException {
        delegate.delete(key);
    }

    @Override
    public String toString() {
        return delegate + ", encrypted with AES-256-GCM";
    }

    byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain);
            byte[] out = new byte[MAGIC.length + IV_BYTES + sealed.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, IV_BYTES);
            System.arraycopy(sealed, 0, out, MAGIC.length + IV_BYTES, sealed.length);
            return out;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    byte[] decrypt(byte[] stored) {
        try {
            byte[] iv = Arrays.copyOfRange(stored, MAGIC.length, MAGIC.length + IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(stored, MAGIC.length + IV_BYTES, stored.length - MAGIC.length - IV_BYTES);
        } catch (GeneralSecurityException ex) {
            throw new StorageException("The file could not be decrypted: wrong key or changed file", ex);
        }
    }

    private static boolean startsWithMagic(byte[] bytes) {
        return bytes.length > MAGIC.length + IV_BYTES
                && Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }
}

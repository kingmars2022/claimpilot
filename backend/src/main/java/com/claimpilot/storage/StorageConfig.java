package com.claimpilot.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.claimpilot.config.AppProperties;

/** Local disk or S3 (RustFS locally), encrypted at rest when a key is configured. */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    FileStorage fileStorage(AppProperties properties) {
        FileStorage storage = "s3".equalsIgnoreCase(properties.storage().type())
                ? S3FileStorage.create(properties.storage().s3())
                : new LocalFileStorage(properties.storage().localRoot());
        String key = properties.storage().encryptionKey();
        if (key == null || key.isBlank()) {
            log.warn("File encryption is off: set claimpilot.storage.encryption-key to encrypt uploads at rest");
        } else {
            storage = new EncryptedFileStorage(storage, key);
        }
        log.info("Uploaded files are kept in {}", storage);
        return storage;
    }
}

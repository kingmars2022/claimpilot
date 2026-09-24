package com.claimpilot.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.claimpilot.config.AppProperties;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    FileStorage fileStorage(AppProperties properties) {
        FileStorage storage = new LocalFileStorage(properties.storage().localRoot());
        String key = properties.storage().encryptionKey();
        if (key == null || key.isBlank()) {
            log.warn("File encryption is off: set claimpilot.storage.encryption-key to encrypt uploads at rest");
            return storage;
        }
        return new EncryptedFileStorage(storage, key);
    }
}

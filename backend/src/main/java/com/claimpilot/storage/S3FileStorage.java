package com.claimpilot.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import com.claimpilot.config.AppProperties;

/**
 * Files in an S3 bucket: Amazon S3 in production, RustFS (S3-compatible, free) locally. Keys have the
 * same shape as on disk, {@code <uuid>/<file name>}, so the two are interchangeable.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static S3FileStorage create(AppProperties.S3 settings) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(settings.region()))
                .forcePathStyle(settings.pathStyle());
        if (settings.endpoint() != null && !settings.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(settings.endpoint()));
        }
        builder.credentialsProvider(settings.accessKey() == null || settings.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())));
        S3FileStorage storage = new S3FileStorage(builder.build(), settings.bucket());
        storage.ensureBucket();
        return storage;
    }

    /** Creates the bucket on first use, which is what a fresh local server needs. */
    void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException ex) {
            s3.createBucket(b -> b.bucket(bucket));
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
            s3.createBucket(b -> b.bucket(bucket));
        }
    }

    @Override
    public String store(String originalFileName, InputStream content) throws IOException {
        String key = UUID.randomUUID() + "/" + LocalFileStorage.sanitize(originalFileName);
        byte[] bytes = content.readAllBytes();
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromBytes(bytes));
        return key;
    }

    @Override
    public Resource load(String key) {
        byte[] bytes = s3.getObject(b -> b.bucket(bucket).key(key), ResponseTransformer.toBytes()).asByteArray();
        String fileName = key.substring(key.indexOf('/') + 1);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public String toString() {
        return "S3 bucket " + bucket;
    }
}
